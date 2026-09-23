package com.wdmmg.expense.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_avatars")
@Getter
@Setter
@NoArgsConstructor
public class UserAvatar {
    @Id
    @Column(name = "avatar_key")
    private UUID key;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "content_type", nullable = false, length = 40)
    private String contentType;

    @Column(nullable = false)
    private byte[] data;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
