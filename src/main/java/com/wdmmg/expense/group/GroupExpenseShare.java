package com.wdmmg.expense.group;

import com.wdmmg.expense.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "group_expense_shares")
@Getter
@Setter
@NoArgsConstructor
public class GroupExpenseShare {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_expense_id")
    private GroupExpense groupExpense;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    public GroupExpenseShare(GroupExpense groupExpense, User user, BigDecimal amount) {
        this.groupExpense = groupExpense;
        this.user = user;
        this.amount = amount;
    }
}
