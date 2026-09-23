package com.wdmmg.expense.money;

import com.wdmmg.expense.common.ApiException;
import com.wdmmg.expense.common.AppClock;
import com.wdmmg.expense.common.Money;
import com.wdmmg.expense.common.PageResponse;
import com.wdmmg.expense.expense.Expense;
import com.wdmmg.expense.expense.ExpenseRepository;
import com.wdmmg.expense.goal.GoalService;
import com.wdmmg.expense.group.GroupExpense;
import com.wdmmg.expense.group.GroupExpenseRepository;
import com.wdmmg.expense.group.Settlement;
import com.wdmmg.expense.group.SettlementRepository;
import com.wdmmg.expense.money.MoneyDtos.*;
import com.wdmmg.expense.notification.NotificationService;
import com.wdmmg.expense.notification.NotificationType;
import com.wdmmg.expense.user.User;
import com.wdmmg.expense.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The user's bank balance, derived from what they record:
 * opening balance + income − personal expenses − group bills they paid
 * − payments they sent + payments they received (all from the opening date on).
 */
@Service
public class MoneyService {
    private static final LocalDate BEGINNING = LocalDate.of(1970, 1, 1);
    private static final LocalDate END = LocalDate.of(9999, 12, 31);

    private final IncomeRepository incomes;
    private final ExpenseRepository expenses;
    private final GroupExpenseRepository groupExpenses;
    private final SettlementRepository settlements;
    private final UserRepository users;
    private final GoalService goals;
    private final NotificationService notifications;
    private final AppClock clock;

    public MoneyService(IncomeRepository incomes, ExpenseRepository expenses, GroupExpenseRepository groupExpenses,
                        SettlementRepository settlements, UserRepository users, GoalService goals,
                        NotificationService notifications, AppClock clock) {
        this.incomes = incomes;
        this.expenses = expenses;
        this.groupExpenses = groupExpenses;
        this.settlements = settlements;
        this.users = users;
        this.goals = goals;
        this.notifications = notifications;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ balance

    @Transactional(readOnly = true)
    public MoneySummary summary(Long userId) {
        User u = user(userId);
        LocalDate from = startOf(u);
        Breakdown b = breakdown(userId, from, END);
        BigDecimal balance = Money.of(u.getOpeningBalance().add(net(b)));
        BigDecimal setAside = goals.totalSaved(userId);

        YearMonth month = YearMonth.from(clock.today());
        LocalDate mStart = max(month.atDay(1), from);
        LocalDate mEnd = month.atEndOfMonth();
        Breakdown m = mStart.isAfter(mEnd) ? zero() : breakdown(userId, mStart, mEnd);
        BigDecimal monthIn = m.income().add(m.paymentsReceived());
        BigDecimal monthOut = m.expenses().add(m.groupBillsPaid()).add(m.paymentsSent());

        return new MoneySummary(u.getOpeningBalanceDate() != null, Money.of(u.getOpeningBalance()), u.getOpeningBalanceDate(),
                balance, setAside, Money.of(balance.subtract(setAside)), Money.of(monthIn), Money.of(monthOut), b,
                u.isSalaryReminder(), u.getSalaryDay());
    }

    /** Every movement in [from, to] (clipped to the opening date), newest first, with running balance. */
    @Transactional(readOnly = true)
    public List<ActivityItem> activity(Long userId, LocalDate from, LocalDate to) {
        User u = user(userId);
        LocalDate opening = startOf(u);
        LocalDate start = max(from, opening);
        if (start.isAfter(to)) return List.of();

        // Balance just before `start`.
        BigDecimal running = u.getOpeningBalance();
        if (start.isAfter(opening)) running = running.add(net(breakdown(userId, opening, start.minusDays(1))));

        record Row(LocalDate date, int order, ActivityKind kind, String key, String title, String detail, BigDecimal amount, String link) {}
        List<Row> rows = new ArrayList<>();
        for (Income i : incomes.findInRange(userId, start, to)) {
            rows.add(new Row(i.getDate(), 0, ActivityKind.INCOME, "i" + i.getId(), i.getName(), label(i.getType()), i.getAmount(), "/money"));
        }
        for (Expense e : expenses.findInRange(userId, start, to)) {
            rows.add(new Row(e.getDate(), 1, ActivityKind.EXPENSE, "e" + e.getId(), e.getName(), e.getCategory().getName(), e.getAmount().negate(), "/expenses"));
        }
        for (GroupExpense ge : groupExpenses.findPaidByInRange(userId, start, to)) {
            rows.add(new Row(ge.getDate(), 1, ActivityKind.GROUP_BILL, "g" + ge.getId(), ge.getName(),
                    "You paid for " + ge.getGroup().getName(), ge.getAmount().negate(), "/groups/" + ge.getGroup().getId()));
        }
        for (Settlement s : settlements.findForUserInRange(userId, start, to)) {
            boolean sent = s.getFromUser().getId().equals(userId);
            rows.add(new Row(s.getDate(), sent ? 1 : 0, sent ? ActivityKind.PAYMENT_SENT : ActivityKind.PAYMENT_RECEIVED, "s" + s.getId(),
                    sent ? "Paid " + s.getToUser().getName() : s.getFromUser().getName() + " paid you",
                    s.getGroup().getName(), sent ? s.getAmount().negate() : s.getAmount(), "/groups/" + s.getGroup().getId()));
        }
        // Oldest first to accumulate the running balance (money in before money out on the same day).
        rows.sort(Comparator.comparing(Row::date).thenComparingInt(Row::order).thenComparing(Row::key));
        List<ActivityItem> out = new ArrayList<>(rows.size());
        for (Row r : rows) {
            running = running.add(r.amount());
            out.add(new ActivityItem(r.key(), r.date(), r.kind(), r.title(), r.detail(), Money.of(r.amount()), Money.of(running), r.link()));
        }
        java.util.Collections.reverse(out);
        return out;
    }

    @Transactional
    public MoneySummary setOpening(Long userId, OpeningBalanceRequest req) {
        User u = user(userId);
        u.setOpeningBalance(Money.of(req.amount()));
        u.setOpeningBalanceDate(req.date());
        users.flush();
        return summary(userId);
    }

    // ------------------------------------------------------------------ income

    @Transactional(readOnly = true)
    public PageResponse<IncomeResponse> listIncome(Long userId, LocalDate from, LocalDate to, int page, int size) {
        var p = incomes.findPage(userId, from == null ? BEGINNING : from, to == null ? END : to,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        return PageResponse.of(p, IncomeResponse::from);
    }

    @Transactional
    public IncomeResponse createIncome(Long userId, IncomeRequest req) {
        Income i = new Income();
        i.setUserId(userId);
        apply(i, req);
        incomes.save(i);
        return IncomeResponse.from(i);
    }

    @Transactional
    public IncomeResponse updateIncome(Long userId, Long id, IncomeRequest req) {
        Income i = incomes.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Income"));
        apply(i, req);
        return IncomeResponse.from(i);
    }

    @Transactional
    public void deleteIncome(Long userId, Long id) {
        incomes.delete(incomes.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Income")));
    }

    // ------------------------------------------------------------------ salary

    @Transactional(readOnly = true)
    public SalaryPrompt salaryPrompt(Long userId) {
        User u = user(userId);
        LocalDate today = clock.today();
        YearMonth month = YearMonth.from(today);
        boolean due = u.isSalaryReminder()
                && today.getDayOfMonth() >= u.getSalaryDay()
                && !month.toString().equals(u.getSalaryPromptDismissed())
                && !hasSalary(userId, month);
        BigDecimal suggested = incomes.findFirstByUserIdAndTypeOrderByDateDescIdDesc(userId, IncomeType.SALARY)
                .map(Income::getAmount).orElse(null);
        return new SalaryPrompt(due, month.toString(), suggested, u.getSalaryDay(), u.isSalaryReminder());
    }

    @Transactional
    public IncomeResponse recordSalary(Long userId, SalaryRequest req) {
        Income i = new Income();
        i.setUserId(userId);
        i.setType(IncomeType.SALARY);
        LocalDate date = req.date() == null ? clock.today() : req.date();
        i.setDate(date);
        i.setName(req.name() == null || req.name().isBlank()
                ? "Salary – " + date.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
                : req.name().trim());
        i.setAmount(Money.of(req.amount()));
        incomes.save(i);
        notifications.resolveForUser(userId, NotificationType.SALARY_DUE);
        return IncomeResponse.from(i);
    }

    /** "No salary this month" – stop asking until next month. */
    @Transactional
    public void dismissSalaryPrompt(Long userId) {
        user(userId).setSalaryPromptDismissed(YearMonth.from(clock.today()).toString());
        notifications.resolveForUser(userId, NotificationType.SALARY_DUE);
    }

    @Transactional
    public SalaryPrompt updateSalarySettings(Long userId, SalarySettingsRequest req) {
        User u = user(userId);
        u.setSalaryReminder(req.reminder());
        u.setSalaryDay(req.day());
        users.flush();
        return salaryPrompt(userId);
    }

    /** Scheduler hook: nudge users whose salary day is today and who haven't logged it yet. */
    @Transactional
    public void sendSalaryReminders() {
        LocalDate today = clock.today();
        YearMonth month = YearMonth.from(today);
        for (User u : users.findBySalaryReminderTrueAndSalaryDay(today.getDayOfMonth())) {
            if (month.toString().equals(u.getSalaryPromptDismissed()) || hasSalary(u.getId(), month)) continue;
            notifications.notify(u.getId(), NotificationType.SALARY_DUE,
                    "Did your salary for " + month.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
                            + " come in? Add it to keep your balance accurate",
                    "/money?salary=1", null, null);
        }
    }

    // ------------------------------------------------------------------ helpers

    private boolean hasSalary(Long userId, YearMonth month) {
        return incomes.existsByUserIdAndTypeAndDateBetween(userId, IncomeType.SALARY, month.atDay(1), month.atEndOfMonth());
    }

    private Breakdown breakdown(Long userId, LocalDate from, LocalDate to) {
        return new Breakdown(
                Money.of(incomes.sum(userId, from, to)),
                Money.of(expenses.sum(userId, from, to)),
                Money.of(groupExpenses.sumPaidBy(userId, from, to)),
                Money.of(settlements.sumSent(userId, from, to)),
                Money.of(settlements.sumReceived(userId, from, to)));
    }

    private static BigDecimal net(Breakdown b) {
        return b.income().add(b.paymentsReceived()).subtract(b.expenses()).subtract(b.groupBillsPaid()).subtract(b.paymentsSent());
    }

    private static Breakdown zero() {
        BigDecimal z = Money.zero();
        return new Breakdown(z, z, z, z, z);
    }

    private static LocalDate startOf(User u) {
        return u.getOpeningBalanceDate() == null ? BEGINNING : u.getOpeningBalanceDate();
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static String label(IncomeType t) {
        String s = t.name().charAt(0) + t.name().substring(1).toLowerCase();
        return s.replace('_', ' ');
    }

    private void apply(Income i, IncomeRequest req) {
        i.setName(req.name().trim());
        i.setAmount(Money.of(req.amount()));
        i.setDate(req.date());
        i.setType(req.type() == null ? IncomeType.OTHER : req.type());
        i.setNotes(req.notes() == null || req.notes().isBlank() ? null : req.notes().trim());
    }

    private User user(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
    }
}
