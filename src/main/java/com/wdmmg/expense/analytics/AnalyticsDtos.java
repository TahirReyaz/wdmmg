package com.wdmmg.expense.analytics;

import com.wdmmg.expense.user.UserDtos.UserSummary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class AnalyticsDtos {
    private AnalyticsDtos() {}

    public record CategoryStat(Long categoryId, String name, String color, BigDecimal total, BigDecimal personal,
                               BigDecimal group, long count, double pct) {}

    public record MethodStat(String method, BigDecimal total, long count) {}

    public record SourceStat(String label, Long groupId, BigDecimal total, long count) {}

    public record DayPoint(LocalDate date, BigDecimal personal, BigDecimal group, BigDecimal total) {}

    public record MonthPoint(String month, BigDecimal personal, BigDecimal group, BigDecimal total) {}

    public record TopItem(String name, BigDecimal amount, LocalDate date, String category, String color,
                          String source, Long groupId) {}

    public record PersonalAnalytics(LocalDate from, LocalDate to, boolean includeGroups,
                                    BigDecimal total, BigDecimal personalTotal, BigDecimal groupShareTotal,
                                    long transactionCount, BigDecimal dailyAverage,
                                    BigDecimal previousTotal, Double changePct,
                                    List<CategoryStat> byCategory, List<MethodStat> byPaymentMethod,
                                    List<SourceStat> bySource, List<DayPoint> daily, List<MonthPoint> monthly,
                                    List<TopItem> topExpenses) {}

    /** One of my shares in a group expense – shown alongside personal expenses. */
    public record GroupShareItem(Long groupExpenseId, Long groupId, String groupName, String name, LocalDate date,
                                 String category, String color, BigDecimal totalAmount, BigDecimal myShare,
                                 String paidBy, boolean paidByMe) {}

    public record MemberStat(UserSummary user, BigDecimal paid, BigDecimal share) {}

    public record GroupAnalytics(Long groupId, LocalDate from, LocalDate to, BigDecimal total, long count,
                                 BigDecimal myShare, List<CategoryStat> byCategory, List<MemberStat> byMember,
                                 List<MonthPoint> monthly) {}
}
