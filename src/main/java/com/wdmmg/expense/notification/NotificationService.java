package com.wdmmg.expense.notification;

import com.wdmmg.expense.common.ApiException;
import com.wdmmg.expense.common.PageResponse;
import com.wdmmg.expense.notification.NotificationDtos.NotificationResponse;
import com.wdmmg.expense.recurring.OccurrenceStatus;
import com.wdmmg.expense.recurring.RecurringOccurrence;
import com.wdmmg.expense.recurring.RecurringOccurrenceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class NotificationService {
    private final NotificationRepository repo;
    private final RecurringOccurrenceRepository occurrences;

    public NotificationService(NotificationRepository repo, RecurringOccurrenceRepository occurrences) {
        this.repo = repo;
        this.occurrences = occurrences;
    }

    /** Joins the caller's transaction so a notification is only stored if the triggering change commits. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void notify(Long userId, NotificationType type, String title, String link, BigDecimal amount, Long refId) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title.length() > 255 ? title.substring(0, 252) + "…" : title);
        n.setLink(link);
        n.setAmount(amount);
        n.setRefId(refId);
        repo.save(n);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(Long userId, boolean unreadOnly, int page, int size) {
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        Page<Notification> p = unreadOnly
                ? repo.findByUserIdAndReadAtIsNullOrderByCreatedAtDescIdDesc(userId, pr)
                : repo.findByUserIdOrderByCreatedAtDescIdDesc(userId, pr);

        // Actionable recurring notifications carry the live state of their occurrence.
        List<Long> occurrenceIds = p.getContent().stream()
                .filter(n -> n.getType() == NotificationType.RECURRING_DUE && n.getRefId() != null)
                .map(Notification::getRefId).toList();
        Map<Long, OccurrenceStatus> states = occurrenceIds.isEmpty() ? Map.of()
                : occurrences.findAllById(occurrenceIds).stream()
                .collect(Collectors.toMap(RecurringOccurrence::getId, RecurringOccurrence::getStatus, (a, b) -> a));

        return PageResponse.of(p, n -> NotificationResponse.from(n,
                n.getType() == NotificationType.RECURRING_DUE ? states.get(n.getRefId()) : null));
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repo.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        Notification n = repo.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Notification"));
        if (n.getReadAt() == null) n.setReadAt(Instant.now());
    }

    @Transactional
    public void markAllRead(Long userId) {
        repo.markAllRead(userId, Instant.now());
    }

    /** Called when the thing a notification asked about has been dealt with elsewhere. */
    @Transactional
    public void resolve(NotificationType type, Long refId) {
        repo.markReadByRef(type, refId, Instant.now());
    }
}
