package com.wdmmg.expense.notification;

import com.wdmmg.expense.recurring.OccurrenceStatus;

import java.math.BigDecimal;
import java.time.Instant;

public final class NotificationDtos {
    private NotificationDtos() {}

    /**
     * actionStatus is set for RECURRING_DUE notifications: PENDING means the user still needs to
     * confirm or skip (refId is the occurrence id).
     */
    public record NotificationResponse(Long id, NotificationType type, String title, String link, BigDecimal amount,
                                       Long refId, boolean read, OccurrenceStatus actionStatus, Instant createdAt) {
        static NotificationResponse from(Notification n, OccurrenceStatus status) {
            return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getLink(), n.getAmount(), n.getRefId(),
                    n.getReadAt() != null, status, n.getCreatedAt());
        }
    }

    public record UnreadCount(long count) {}
}
