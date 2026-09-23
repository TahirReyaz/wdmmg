package com.wdmmg.expense.recurring;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One due instance of a recurring expense, waiting for the user to confirm or skip it. */
@Entity
@Table(name = "recurring_occurrences")
@Getter
@Setter
@NoArgsConstructor
public class RecurringOccurrence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recurring_id")
    private RecurringExpense recurring;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private OccurrenceStatus status = OccurrenceStatus.PENDING;

    @Column(name = "expense_id")
    private Long expenseId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
