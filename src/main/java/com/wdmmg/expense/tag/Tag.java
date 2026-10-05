package com.wdmmg.expense.tag;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A user's own label for expenses that belong together across categories, e.g. "Goa trip". */
@Entity
@Table(name = "tags")
@Getter
@Setter
@NoArgsConstructor
public class Tag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, length = 9)
    private String color = "#2a78d6";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
