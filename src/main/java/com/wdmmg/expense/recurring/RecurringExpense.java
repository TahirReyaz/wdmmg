package com.wdmmg.expense.recurring;

import com.wdmmg.expense.category.Category;
import com.wdmmg.expense.expense.PaymentMethod;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "recurring_expenses")
@Getter
@Setter
@NoArgsConstructor
public class RecurringExpense {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod = PaymentMethod.OTHER;

    @Column(length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Frequency frequency;

    /** Repeat every N units of frequency, e.g. every 2 weeks. */
    @Column(name = "interval_count", nullable = false)
    private int intervalCount = 1;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /** How many occurrences have been raised so far; the next due date is occurrence(n). */
    @Column(name = "occurrences_generated", nullable = false)
    private int occurrencesGenerated;

    /** Null once the schedule has passed its end date. */
    @Column(name = "next_due_date")
    private LocalDate nextDueDate;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public LocalDate occurrence(int n) {
        return frequency.occurrence(startDate, intervalCount, n);
    }

    /** Recomputes nextDueDate from occurrencesGenerated, honouring the end date. */
    public void refreshNextDue() {
        LocalDate next = occurrence(occurrencesGenerated);
        nextDueDate = endDate != null && next.isAfter(endDate) ? null : next;
    }
}
