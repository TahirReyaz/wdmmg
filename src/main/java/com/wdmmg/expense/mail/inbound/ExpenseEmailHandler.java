package com.wdmmg.expense.mail.inbound;

import com.wdmmg.expense.ai.AiException;
import com.wdmmg.expense.ai.AiProperties;
import com.wdmmg.expense.ai.ExpenseDraft;
import com.wdmmg.expense.ai.ExpenseTextInterpreter;
import com.wdmmg.expense.ai.Interpretation;
import com.wdmmg.expense.ai.InterpretationRequest;
import com.wdmmg.expense.category.Category;
import com.wdmmg.expense.category.CategoryRepository;
import com.wdmmg.expense.common.AppClock;
import com.wdmmg.expense.expense.ExpenseDtos.ExpenseRequest;
import com.wdmmg.expense.expense.ExpenseDtos.ExpenseResponse;
import com.wdmmg.expense.expense.ExpenseService;
import com.wdmmg.expense.expense.PaymentMethod;
import com.wdmmg.expense.mail.EmailTemplates;
import com.wdmmg.expense.mail.EmailTemplates.AddedLine;
import com.wdmmg.expense.notification.NotificationService;
import com.wdmmg.expense.notification.NotificationType;
import com.wdmmg.expense.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * "I had an ice cream for 30rs" → a saved expense. The configured
 * {@link ExpenseTextInterpreter} proposes drafts; this handler saves them, notifies the
 * user in the app and replies by email with what was added (or why nothing was).
 * Only runs for mail from verified users.
 */
@Component
@Order(100) // catch-all for users; more specific handlers (commands, receipts) go before it
public class ExpenseEmailHandler implements InboundEmailHandler {
    private static final Logger log = LoggerFactory.getLogger(ExpenseEmailHandler.class);
    private static final String EMAIL_NOTE = "Added from email";

    private final ExpenseTextInterpreter interpreter;
    private final CategoryRepository categories;
    private final ExpenseService expenses;
    private final NotificationService notifications;
    private final EmailTemplates templates;
    private final AppClock clock;
    private final AiProperties ai;
    private final String appUrl;

    public ExpenseEmailHandler(ExpenseTextInterpreter interpreter, CategoryRepository categories, ExpenseService expenses,
                               NotificationService notifications, EmailTemplates templates, AppClock clock, AiProperties ai,
                               @Value("${app.public-url:}") String appUrl) {
        this.interpreter = interpreter;
        this.categories = categories;
        this.expenses = expenses;
        this.notifications = notifications;
        this.templates = templates;
        this.clock = clock;
        this.ai = ai;
        this.appUrl = appUrl == null || appUrl.isBlank() ? null : appUrl.replaceAll("/+$", "");
    }

    @Override
    public String name() {
        return "expense-from-text";
    }

    @Override
    public boolean supports(InboundContext ctx) {
        return ctx.fromKnownUser() && hasContent(ctx.email());
    }

    @Override
    public HandlerResult handle(InboundContext ctx) {
        ReceivedEmail e = ctx.email();
        User user = ctx.user();
        String currency = ai.currency() == null || ai.currency().isBlank() ? "INR" : ai.currency().trim().toUpperCase();
        Map<String, Category> byName = new LinkedHashMap<>();
        categories.findByActiveTrueOrderBySortOrderAscNameAsc().forEach(c -> byName.put(c.getName().toLowerCase(), c));
        List<String> names = byName.values().stream().map(Category::getName).toList();
        InterpretationRequest request = new InterpretationRequest(user.getId(), e.subject(), e.text(), clock.today(), names, currency);

        Interpretation result;
        try {
            result = interpreter.interpret(request);
        } catch (AiException ex) {
            log.warn("Couldn't interpret email {} from user {}: {}", e.messageId(), user.getId(), ex.getMessage());
            return new HandlerResult(InboundStatus.FAILED, "AI error: " + ex.getMessage(),
                    templates.couldNotProcess(user.getEmail(), user.getName(), e.subject(), e.messageId()));
        }

        // AI switched off: keep the old behaviour – logged, no reply.
        if ("none".equals(interpreter.provider())) {
            return HandlerResult.processed("Logged for " + user.getEmail() + " – AI parsing is off");
        }
        if (!result.isExpense() || result.expenses().isEmpty()) {
            return new HandlerResult(InboundStatus.PROCESSED, "Not an expense: " + result.reason(),
                    templates.notAnExpense(user.getEmail(), user.getName(), e.subject(), e.messageId(), result.reason()));
        }

        List<AddedLine> added = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (ExpenseDraft d : result.expenses()) {
            if (!currency.equalsIgnoreCase(d.currency())) {
                skipped.add(d.name() + " (" + d.currency() + " " + d.amount().toPlainString() + " – only " + currency + " amounts are supported)");
                continue;
            }
            Category category = byName.get(d.category().toLowerCase());
            if (category == null) category = byName.values().stream().reduce((a, b) -> b).orElse(null);
            if (category == null) {
                skipped.add(d.name() + " (no expense types are set up)");
                continue;
            }
            try {
                String notes = d.notes() == null ? EMAIL_NOTE : truncate(d.notes() + " · " + EMAIL_NOTE, 1000);
                PaymentMethod method = d.paymentMethod() == null ? null : PaymentMethod.valueOf(d.paymentMethod());
                ExpenseResponse saved = expenses.create(user.getId(),
                        new ExpenseRequest(d.name(), d.amount(), d.date(), category.getId(), method, notes, null));
                notifications.notify(user.getId(), NotificationType.EXPENSE_FROM_EMAIL, "Added from your email: " + d.name(),
                        "/expenses", d.amount(), saved.id());
                added.add(new AddedLine(d.name(), d.amount(), currency, d.date(), category.getName()));
            } catch (RuntimeException ex) {
                log.warn("Couldn't save '{}' from email {}: {}", d.name(), e.messageId(), ex.getMessage());
                skipped.add(d.name() + " (couldn't be saved)");
            }
        }

        String summary = added.isEmpty() ? "Nothing saved" : "Added " + added.stream()
                .map(a -> a.name() + " " + a.amount().toPlainString() + " [" + a.category() + ", " + a.date() + "]")
                .collect(Collectors.joining("; "));
        if (!skipped.isEmpty()) summary += " | Skipped: " + String.join("; ", skipped);

        var reply = added.isEmpty()
                ? templates.notAnExpense(user.getEmail(), user.getName(), e.subject(), e.messageId(),
                        "These couldn't be added: " + String.join("; ", skipped) + ".")
                : templates.expensesAdded(user.getEmail(), user.getName(), e.subject(), e.messageId(), appUrl, added, skipped);
        return new HandlerResult(added.isEmpty() ? InboundStatus.FAILED : InboundStatus.PROCESSED, summary, reply);
    }

    private static boolean hasContent(ReceivedEmail e) {
        return (e.text() != null && !e.text().isBlank()) || (e.subject() != null && !e.subject().isBlank());
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
