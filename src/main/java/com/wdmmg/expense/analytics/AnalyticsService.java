package com.wdmmg.expense.analytics;

import com.wdmmg.expense.analytics.AnalyticsDtos.*;
import com.wdmmg.expense.category.Category;
import com.wdmmg.expense.common.Money;
import com.wdmmg.expense.expense.Expense;
import com.wdmmg.expense.expense.ExpenseRepository;
import com.wdmmg.expense.group.*;
import com.wdmmg.expense.security.AuthUser;
import com.wdmmg.expense.user.User;
import com.wdmmg.expense.user.UserDtos.UserSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {
    private static final int DAILY_SERIES_MAX_DAYS = 92;
    private static final LocalDate MIN_DATE = LocalDate.of(1970, 1, 1);

    private final ExpenseRepository expenses;
    private final GroupExpenseRepository groupExpenses;
    private final GroupService groupService;

    public AnalyticsService(ExpenseRepository expenses, GroupExpenseRepository groupExpenses,
                            GroupService groupService) {
        this.expenses = expenses;
        this.groupExpenses = groupExpenses;
        this.groupService = groupService;
    }

    /** A spend record normalised across personal expenses and my shares of group expenses. */
    private record Entry(String name, BigDecimal amount, LocalDate date, Category category, String method,
                         Long groupId, String groupName) {
        boolean isGroup() {
            return groupId != null;
        }
    }

    // ------------------------------------------------------------------ personal

    @Transactional(readOnly = true)
    public PersonalAnalytics personal(Long userId, LocalDate from, LocalDate to, boolean includeGroups) {
        LocalDate today = LocalDate.now();
        LocalDate end = to != null ? to : today;
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        if (start.isAfter(end)) { LocalDate t = start; start = end; end = t; }

        long days = ChronoUnit.DAYS.between(start, end) + 1;
        LocalDate prevStart = start.minusDays(days);
        LocalDate trendStart = YearMonth.from(end).minusMonths(11).atDay(1);
        LocalDate fetchStart = min(prevStart, trendStart);

        List<Entry> all = load(userId, fetchStart, end, includeGroups);
        final LocalDate s = start, e = end;
        List<Entry> current = all.stream().filter(x -> !x.date().isBefore(s) && !x.date().isAfter(e)).toList();
        BigDecimal previous = sum(all.stream()
                .filter(x -> !x.date().isBefore(prevStart) && x.date().isBefore(s)).toList());

        BigDecimal total = sum(current);
        BigDecimal personal = sum(current.stream().filter(x -> !x.isGroup()).toList());
        BigDecimal group = total.subtract(personal);
        Double change = previous.signum() == 0 ? null
                : total.subtract(previous).multiply(BigDecimal.valueOf(100))
                .divide(previous, 1, RoundingMode.HALF_UP).doubleValue();

        return new PersonalAnalytics(start, end, includeGroups, Money.of(total), Money.of(personal), Money.of(group),
                current.size(), Money.of(total.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP)),
                Money.of(previous), change,
                byCategory(current, total), byMethod(current), bySource(current),
                days <= DAILY_SERIES_MAX_DAYS ? daily(current, start, end) : List.of(),
                monthly(all.stream().filter(x -> !x.date().isBefore(trendStart)).toList(),
                        YearMonth.from(trendStart), YearMonth.from(end)),
                top(current));
    }

    @Transactional(readOnly = true)
    public List<GroupShareItem> groupShares(Long userId, LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        return groupExpenses.findUserSharesInRange(userId, start, end).stream().map(sh -> {
            GroupExpense ge = sh.getGroupExpense();
            User payer = ge.getPaidBy();
            return new GroupShareItem(ge.getId(), ge.getGroup().getId(), ge.getGroup().getName(), ge.getName(),
                    ge.getDate(), ge.getCategory().getName(), ge.getCategory().getColor(), ge.getAmount(),
                    sh.getAmount(), payer.getName(), payer.getId().equals(userId));
        }).toList();
    }

    private List<Entry> load(Long userId, LocalDate from, LocalDate to, boolean includeGroups) {
        List<Entry> out = new ArrayList<>();
        for (Expense x : expenses.findInRange(userId, from, to)) {
            out.add(new Entry(x.getName(), x.getAmount(), x.getDate(), x.getCategory(),
                    x.getPaymentMethod().name(), null, null));
        }
        if (includeGroups) {
            for (GroupExpenseShare sh : groupExpenses.findUserSharesInRange(userId, from, to)) {
                GroupExpense ge = sh.getGroupExpense();
                out.add(new Entry(ge.getName(), sh.getAmount(), ge.getDate(), ge.getCategory(), null,
                        ge.getGroup().getId(), ge.getGroup().getName()));
            }
        }
        return out;
    }

    private List<CategoryStat> byCategory(List<Entry> list, BigDecimal total) {
        Map<Long, List<Entry>> grouped = list.stream()
                .collect(Collectors.groupingBy(x -> x.category().getId(), LinkedHashMap::new, Collectors.toList()));
        List<CategoryStat> out = new ArrayList<>();
        grouped.forEach((id, items) -> {
            Category c = items.get(0).category();
            BigDecimal t = sum(items);
            BigDecimal g = sum(items.stream().filter(Entry::isGroup).toList());
            out.add(new CategoryStat(id, c.getName(), c.getColor(), Money.of(t), Money.of(t.subtract(g)),
                    Money.of(g), items.size(), pct(t, total)));
        });
        out.sort(Comparator.comparing(CategoryStat::total).reversed());
        return out;
    }

    private List<MethodStat> byMethod(List<Entry> list) {
        Map<String, List<Entry>> grouped = list.stream().filter(x -> !x.isGroup())
                .collect(Collectors.groupingBy(Entry::method));
        return grouped.entrySet().stream()
                .map(en -> new MethodStat(en.getKey(), Money.of(sum(en.getValue())), en.getValue().size()))
                .sorted(Comparator.comparing(MethodStat::total).reversed()).toList();
    }

    private List<SourceStat> bySource(List<Entry> list) {
        List<SourceStat> out = new ArrayList<>();
        List<Entry> personal = list.stream().filter(x -> !x.isGroup()).toList();
        if (!personal.isEmpty()) out.add(new SourceStat("Personal", null, Money.of(sum(personal)), personal.size()));
        list.stream().filter(Entry::isGroup)
                .collect(Collectors.groupingBy(Entry::groupId, LinkedHashMap::new, Collectors.toList()))
                .forEach((gid, items) -> out.add(new SourceStat(items.get(0).groupName(), gid,
                        Money.of(sum(items)), items.size())));
        out.sort(Comparator.comparing(SourceStat::total).reversed());
        return out;
    }

    private List<DayPoint> daily(List<Entry> list, LocalDate start, LocalDate end) {
        Map<LocalDate, BigDecimal[]> m = new TreeMap<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            m.put(d, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        }
        for (Entry x : list) {
            BigDecimal[] v = m.get(x.date());
            if (v == null) continue;
            if (x.isGroup()) v[1] = v[1].add(x.amount());
            else v[0] = v[0].add(x.amount());
        }
        return m.entrySet().stream().map(en -> new DayPoint(en.getKey(), Money.of(en.getValue()[0]),
                Money.of(en.getValue()[1]), Money.of(en.getValue()[0].add(en.getValue()[1])))).toList();
    }

    private List<MonthPoint> monthly(List<Entry> list, YearMonth first, YearMonth last) {
        Map<YearMonth, BigDecimal[]> m = new TreeMap<>();
        for (YearMonth ym = first; !ym.isAfter(last); ym = ym.plusMonths(1)) {
            m.put(ym, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        }
        for (Entry x : list) {
            BigDecimal[] v = m.get(YearMonth.from(x.date()));
            if (v == null) continue;
            if (x.isGroup()) v[1] = v[1].add(x.amount());
            else v[0] = v[0].add(x.amount());
        }
        return m.entrySet().stream().map(en -> new MonthPoint(en.getKey().toString(), Money.of(en.getValue()[0]),
                Money.of(en.getValue()[1]), Money.of(en.getValue()[0].add(en.getValue()[1])))).toList();
    }

    private List<TopItem> top(List<Entry> list) {
        return list.stream().sorted(Comparator.comparing(Entry::amount).reversed()).limit(5)
                .map(x -> new TopItem(x.name(), Money.of(x.amount()), x.date(), x.category().getName(),
                        x.category().getColor(), x.isGroup() ? x.groupName() : "Personal", x.groupId()))
                .toList();
    }

    // ------------------------------------------------------------------ group

    @Transactional(readOnly = true)
    public GroupAnalytics group(AuthUser me, Long groupId, LocalDate from, LocalDate to) {
        var detail = groupService.detail(me, groupId); // also checks membership
        LocalDate start = from != null ? from : MIN_DATE;
        LocalDate end = to != null ? to : LocalDate.now().plusYears(100);
        List<GroupExpense> list = groupExpenses.findInRange(groupId, start, end);

        BigDecimal total = list.stream().map(GroupExpense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal myShare = BigDecimal.ZERO;

        // categories
        Map<Long, List<GroupExpense>> byCat = list.stream()
                .collect(Collectors.groupingBy(x -> x.getCategory().getId(), LinkedHashMap::new, Collectors.toList()));
        List<CategoryStat> cats = new ArrayList<>();
        final BigDecimal fTotal = total;
        byCat.forEach((id, items) -> {
            Category c = items.get(0).getCategory();
            BigDecimal t = items.stream().map(GroupExpense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            cats.add(new CategoryStat(id, c.getName(), c.getColor(), Money.of(t), Money.zero(), Money.of(t),
                    items.size(), pct(t, fTotal)));
        });
        cats.sort(Comparator.comparing(CategoryStat::total).reversed());

        // members: paid vs share within the range
        Map<Long, BigDecimal> paid = new HashMap<>();
        Map<Long, BigDecimal> share = new HashMap<>();
        Map<Long, UserSummary> people = new LinkedHashMap<>();
        detail.members().forEach(mb -> people.put(mb.user().id(), mb.user()));
        for (GroupExpense ge : list) {
            paid.merge(ge.getPaidBy().getId(), ge.getAmount(), BigDecimal::add);
            for (GroupExpenseShare sh : ge.getShares()) {
                share.merge(sh.getUser().getId(), sh.getAmount(), BigDecimal::add);
                people.computeIfAbsent(sh.getUser().getId(), k -> UserSummary.from(sh.getUser()));
                if (sh.getUser().getId().equals(me.id())) myShare = myShare.add(sh.getAmount());
            }
        }
        List<MemberStat> members = people.values().stream()
                .map(u -> new MemberStat(u, Money.of(paid.getOrDefault(u.id(), BigDecimal.ZERO)),
                        Money.of(share.getOrDefault(u.id(), BigDecimal.ZERO))))
                .sorted(Comparator.comparing(MemberStat::paid).reversed()).toList();

        // monthly (only months that fall in data range)
        List<MonthPoint> monthly = List.of();
        if (!list.isEmpty()) {
            LocalDate minD = list.stream().map(GroupExpense::getDate).min(LocalDate::compareTo).orElseThrow();
            LocalDate maxD = list.stream().map(GroupExpense::getDate).max(LocalDate::compareTo).orElseThrow();
            YearMonth firstMonth = YearMonth.from(minD);
            YearMonth lastMonth = YearMonth.from(maxD);
            if (firstMonth.isBefore(lastMonth.minusMonths(23))) firstMonth = lastMonth.minusMonths(23);
            Map<YearMonth, BigDecimal> m = new TreeMap<>();
            for (YearMonth ym = firstMonth; !ym.isAfter(lastMonth); ym = ym.plusMonths(1)) m.put(ym, BigDecimal.ZERO);
            for (GroupExpense ge : list) m.computeIfPresent(YearMonth.from(ge.getDate()), (k, v) -> v.add(ge.getAmount()));
            monthly = m.entrySet().stream()
                    .map(en -> new MonthPoint(en.getKey().toString(), Money.zero(), Money.of(en.getValue()),
                            Money.of(en.getValue()))).toList();
        }

        return new GroupAnalytics(groupId, from, to, Money.of(total), list.size(), Money.of(myShare),
                cats, members, monthly);
    }

    // ------------------------------------------------------------------ utils

    private static BigDecimal sum(List<Entry> list) {
        return list.stream().map(Entry::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static double pct(BigDecimal part, BigDecimal total) {
        if (total.signum() == 0) return 0;
        return part.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP).doubleValue();
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
