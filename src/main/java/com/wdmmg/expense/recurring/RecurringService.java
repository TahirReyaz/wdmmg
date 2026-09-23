package com.wdmmg.expense.recurring;

import com.wdmmg.expense.category.CategoryService;
import com.wdmmg.expense.common.ApiException;
import com.wdmmg.expense.common.AppClock;
import com.wdmmg.expense.common.Money;
import com.wdmmg.expense.expense.Expense;
import com.wdmmg.expense.expense.ExpenseRepository;
import com.wdmmg.expense.expense.PaymentMethod;
import com.wdmmg.expense.notification.NotificationService;
import com.wdmmg.expense.notification.NotificationType;
import com.wdmmg.expense.recurring.RecurringDtos.*;
import com.wdmmg.expense.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class RecurringService {
    private static final Logger log = LoggerFactory.getLogger(RecurringService.class);
    /** Safety valve if the server was down for a long time: raise at most this many backlog items per run. */
    private static final int MAX_CATCH_UP = 24;

    private final RecurringExpenseRepository templates;
    private final RecurringOccurrenceRepository occurrences;
    private final ExpenseRepository expenses;
    private final UserRepository users;
    private final CategoryService categories;
    private final NotificationService notifications;
    private final AppClock clock;

    public RecurringService(RecurringExpenseRepository templates, RecurringOccurrenceRepository occurrences,
                            ExpenseRepository expenses, UserRepository users, CategoryService categories,
                            NotificationService notifications, AppClock clock) {
        this.templates = templates;
        this.occurrences = occurrences;
        this.expenses = expenses;
        this.users = users;
        this.categories = categories;
        this.notifications = notifications;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ templates

    @Transactional(readOnly = true)
    public List<RecurringResponse> list(Long userId) {
        List<RecurringExpense> rows = templates.findForUser(userId);
        Map<Long, Long> pending = occurrences.pendingCounts(rows.stream().map(RecurringExpense::getId).toList());
        return rows.stream().map(r -> RecurringResponse.from(r, pending.getOrDefault(r.getId(), 0L))).toList();
    }

    @Transactional
    public RecurringResponse create(Long userId, RecurringRequest req) {
        validate(req);
        RecurringExpense r = new RecurringExpense();
        r.setUserId(userId);
        r.setCategory(categories.requireUsable(req.categoryId()));
        apply(r, req);
        fastForward(r, clock.today());
        templates.save(r);
        raiseDue(r, clock.today());
        return RecurringResponse.from(r, occurrences.pendingCounts(List.of(r.getId())).getOrDefault(r.getId(), 0L));
    }

    @Transactional
    public RecurringResponse update(Long userId, Long id, RecurringRequest req) {
        validate(req);
        RecurringExpense r = owned(userId, id);
        boolean scheduleChanged = r.getFrequency() != req.frequency()
                || r.getIntervalCount() != interval(req)
                || !r.getStartDate().equals(req.startDate())
                || !Objects.equals(r.getEndDate(), req.endDate());
        if (!r.getCategory().getId().equals(req.categoryId())) r.setCategory(categories.requireUsable(req.categoryId()));
        apply(r, req);
        if (scheduleChanged) fastForward(r, clock.today());
        else r.refreshNextDue();
        raiseDue(r, clock.today());
        return RecurringResponse.from(r, occurrences.pendingCounts(List.of(r.getId())).getOrDefault(r.getId(), 0L));
    }

    @Transactional
    public RecurringResponse setActive(Long userId, Long id, boolean active) {
        RecurringExpense r = owned(userId, id);
        if (r.isActive() != active) {
            r.setActive(active);
            if (active) {
                // Resuming doesn't raise everything missed while paused – it picks up from today.
                fastForward(r, clock.today());
                raiseDue(r, clock.today());
            }
        }
        return RecurringResponse.from(r, occurrences.pendingCounts(List.of(r.getId())).getOrDefault(r.getId(), 0L));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        RecurringExpense r = owned(userId, id);
        occurrences.findPending(userId).stream()
                .filter(o -> o.getRecurring().getId().equals(id))
                .forEach(o -> notifications.resolve(NotificationType.RECURRING_DUE, o.getId()));
        templates.delete(r);
    }

    // ------------------------------------------------------------------ occurrences

    @Transactional(readOnly = true)
    public List<OccurrenceResponse> pending(Long userId) {
        return occurrences.findPending(userId).stream().map(OccurrenceResponse::from).toList();
    }

    /** Turns a due occurrence into a real expense. */
    @Transactional
    public OccurrenceResponse confirm(Long userId, Long occurrenceId, ConfirmRequest req) {
        RecurringOccurrence o = pendingOwned(userId, occurrenceId);
        RecurringExpense r = o.getRecurring();
        Expense e = new Expense();
        e.setUser(users.getReferenceById(userId));
        e.setName(r.getName());
        e.setAmount(Money.of(req != null && req.amount() != null ? req.amount() : o.getAmount()));
        e.setDate(req != null && req.date() != null ? req.date() : o.getDueDate());
        // Kept even if the category has since been retired – it's what the user set up.
        e.setCategory(r.getCategory());
        e.setPaymentMethod(r.getPaymentMethod());
        e.setNotes(r.getNotes());
        expenses.save(e);

        o.setStatus(OccurrenceStatus.CONFIRMED);
        o.setExpenseId(e.getId());
        o.setResolvedAt(Instant.now());
        notifications.resolve(NotificationType.RECURRING_DUE, o.getId());
        return OccurrenceResponse.from(o);
    }

    @Transactional
    public OccurrenceResponse skip(Long userId, Long occurrenceId) {
        RecurringOccurrence o = pendingOwned(userId, occurrenceId);
        o.setStatus(OccurrenceStatus.SKIPPED);
        o.setResolvedAt(Instant.now());
        notifications.resolve(NotificationType.RECURRING_DUE, o.getId());
        return OccurrenceResponse.from(o);
    }

    // ------------------------------------------------------------------ scheduling

    /** Called by the scheduler for each template that has come due. */
    @Transactional
    public void processDue(Long templateId, LocalDate today) {
        templates.findById(templateId).ifPresent(r -> raiseDue(r, today));
    }

    /**
     * Raises a pending occurrence (and a notification asking for confirmation) for every
     * scheduled date up to and including today.
     */
    void raiseDue(RecurringExpense r, LocalDate today) {
        int raised = 0;
        while (r.isActive() && r.getNextDueDate() != null && !r.getNextDueDate().isAfter(today)) {
            if (raised >= MAX_CATCH_UP) {
                log.warn("Recurring expense {} had more than {} missed dates; skipping ahead to today", r.getId(), MAX_CATCH_UP);
                fastForward(r, today.plusDays(1));
                break;
            }
            LocalDate due = r.getNextDueDate();
            if (!occurrences.existsByRecurringIdAndDueDate(r.getId(), due)) {
                RecurringOccurrence o = new RecurringOccurrence();
                o.setRecurring(r);
                o.setUserId(r.getUserId());
                o.setDueDate(due);
                o.setAmount(r.getAmount());
                occurrences.save(o);
                notifications.notify(r.getUserId(), NotificationType.RECURRING_DUE,
                        "“" + r.getName() + "” is due – confirm to add it to your expenses",
                        "/expenses?view=recurring", r.getAmount(), o.getId());
                raised++;
            }
            r.setOccurrencesGenerated(r.getOccurrencesGenerated() + 1);
            r.refreshNextDue();
        }
    }

    /** Moves the schedule to the first occurrence on or after {@code from}. */
    static void fastForward(RecurringExpense r, LocalDate from) {
        int n = 0;
        // Bounded: daily for 50 years is ~18k steps.
        while (r.occurrence(n).isBefore(from) && n < 20_000) n++;
        r.setOccurrencesGenerated(n);
        r.refreshNextDue();
    }

    // ------------------------------------------------------------------ helpers

    private void apply(RecurringExpense r, RecurringRequest req) {
        r.setName(req.name().trim());
        r.setAmount(Money.of(req.amount()));
        r.setPaymentMethod(req.paymentMethod() == null ? PaymentMethod.OTHER : req.paymentMethod());
        r.setNotes(req.notes() == null || req.notes().isBlank() ? null : req.notes().trim());
        r.setFrequency(req.frequency());
        r.setIntervalCount(interval(req));
        r.setStartDate(req.startDate());
        r.setEndDate(req.endDate());
    }

    private static int interval(RecurringRequest req) {
        return req.intervalCount() == null ? 1 : req.intervalCount();
    }

    private static void validate(RecurringRequest req) {
        if (req.endDate() != null && req.endDate().isBefore(req.startDate())) {
            throw ApiException.badRequest("End date must be on or after the start date");
        }
    }

    private RecurringExpense owned(Long userId, Long id) {
        return templates.findOwned(id, userId).orElseThrow(() -> ApiException.notFound("Recurring expense"));
    }

    private RecurringOccurrence pendingOwned(Long userId, Long id) {
        RecurringOccurrence o = occurrences.findOwned(id, userId).orElseThrow(() -> ApiException.notFound("Due expense"));
        if (o.getStatus() != OccurrenceStatus.PENDING) {
            throw ApiException.conflict(o.getStatus() == OccurrenceStatus.CONFIRMED ? "This one was already added" : "This one was already skipped");
        }
        return o;
    }
}
