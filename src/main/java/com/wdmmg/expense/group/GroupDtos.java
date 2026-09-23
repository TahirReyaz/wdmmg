package com.wdmmg.expense.group;

import com.wdmmg.expense.category.CategoryDtos.CategoryResponse;
import com.wdmmg.expense.user.UserDtos.UserSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class GroupDtos {
    private GroupDtos() {}

    // ---------- requests ----------
    public record GroupRequest(@NotBlank @Size(max = 100) String name, @Size(max = 500) String description) {}

    public record AddMemberRequest(@NotBlank @Email String email) {}

    /**
     * value meaning depends on split type: ignored for EQUAL (presence = participates),
     * amount for EXACT, percentage for PERCENT, weight units for SHARES.
     */
    public record ShareInput(@NotNull Long userId, BigDecimal value) {}

    public record GroupExpenseRequest(
            @NotBlank @Size(max = 150) String name,
            @NotNull @DecimalMin(value = "0.01", message = "must be greater than 0")
            @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotNull LocalDate date,
            @NotNull Long categoryId,
            @NotNull Long paidById,
            @NotNull SplitType splitType,
            @Size(max = 1000) String notes,
            @Valid List<ShareInput> shares) {}

    /** A payment made by the signed-in user to another member. */
    public record SettlementRequest(
            @NotNull Long toUserId,
            @NotNull @DecimalMin(value = "0.01", message = "must be greater than 0")
            @Digits(integer = 10, fraction = 2) BigDecimal amount,
            LocalDate date,
            @Size(max = 255) String note) {}

    // ---------- responses ----------
    public record GroupSummary(Long id, String name, String description, UserSummary createdBy, Instant createdAt,
                               long memberCount, BigDecimal totalSpent, BigDecimal myBalance) {}

    /** member=false marks a former member who still appears because of past expenses. */
    public record MemberBalance(UserSummary user, BigDecimal paid, BigDecimal share, BigDecimal settledOut,
                                BigDecimal settledIn, BigDecimal net, boolean member) {}

    public record Debt(UserSummary from, UserSummary to, BigDecimal amount) {}

    public record GroupDetail(Long id, String name, String description, UserSummary createdBy, Instant createdAt,
                              BigDecimal totalSpent, BigDecimal myBalance, List<MemberBalance> members,
                              List<Debt> simplifiedDebts) {}

    public record ShareResponse(UserSummary user, BigDecimal amount) {}

    public record GroupExpenseResponse(Long id, Long groupId, String name, BigDecimal amount, LocalDate date,
                                       CategoryResponse category, UserSummary paidBy, UserSummary createdBy,
                                       SplitType splitType, String notes, List<ShareResponse> shares,
                                       BigDecimal myShare, Instant createdAt) {}

    public record SettlementResponse(Long id, UserSummary from, UserSummary to, BigDecimal amount, LocalDate date,
                                     String note, Instant createdAt) {}

    public record OverallBalance(BigDecimal youOwe, BigDecimal youAreOwed, BigDecimal net, List<GroupSummary> groups) {}
}
