package com.tahir.finance.category.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "expense_categories")
@Getter
@Setter
public class ExpenseCategory {

    @Id
    private UUID id;

    /** NULL means a system category shared by every user. */
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private String name;

    private String icon;

    @Column(name = "color_hex", length = 7)
    private String colorHex;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder = 0;

    @Column(name = "is_archived", nullable = false)
    private boolean archived = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
