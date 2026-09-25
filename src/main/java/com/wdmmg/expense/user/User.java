package com.wdmmg.expense.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    /** Set once the user proves they own the address with a one-time code. */
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    /** Key of the current profile picture in user_avatars, or null for the initials placeholder. */
    @Column(name = "avatar_key")
    private UUID avatarKey;

    /** Bank balance at the start of openingBalanceDate; transactions from that day on move it. */
    @Column(name = "opening_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @Column(name = "opening_balance_date")
    private LocalDate openingBalanceDate;

    @Column(name = "salary_reminder", nullable = false)
    private boolean salaryReminder = true;

    /** Day of month (1–28) the salary usually arrives; the salary prompt appears from this day. */
    @Column(name = "salary_day", nullable = false)
    private int salaryDay = 1;

    /** "YYYY-MM" the user dismissed the salary prompt for. */
    @Column(name = "salary_prompt_dismissed", length = 7)
    private String salaryPromptDismissed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
