package com.wdmmg.expense.mail.inbound;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Audit record of a message picked up from the inbound mailbox; message_id makes pickup idempotent. */
@Entity
@Table(name = "inbound_emails")
@Getter
@Setter
@NoArgsConstructor
public class InboundEmail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false, unique = true, length = 512)
    private String messageId;

    @Column(name = "from_address", nullable = false, length = 320)
    private String fromAddress;

    @Column(name = "from_name", length = 200)
    private String fromName;

    @Column(length = 998)
    private String subject;

    @Column(name = "body_text", columnDefinition = "text")
    private String bodyText;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    /** Matched sender, if the address belongs to a verified user. */
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InboundStatus status;

    @Column(length = 60)
    private String handler;

    @Column(columnDefinition = "text")
    private String result;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
