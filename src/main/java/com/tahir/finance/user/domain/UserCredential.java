package com.tahir.finance.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Split from {@link User} so that reading a profile never pulls the password
 * hash into memory.
 */
@Entity
@Table(name = "user_credentials")
@Getter
@Setter
public class UserCredential {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "password_set_at", nullable = false)
    private Instant passwordSetAt = Instant.now();
}
