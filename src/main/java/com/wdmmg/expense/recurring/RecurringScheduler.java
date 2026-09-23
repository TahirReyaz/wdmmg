package com.wdmmg.expense.recurring;

import com.wdmmg.expense.common.AppClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Raises due recurring expenses: on startup and every 30 minutes (so a new day is picked up promptly). */
@Component
public class RecurringScheduler {
    private static final Logger log = LoggerFactory.getLogger(RecurringScheduler.class);

    private final RecurringExpenseRepository templates;
    private final RecurringService service;
    private final AppClock clock;

    public RecurringScheduler(RecurringExpenseRepository templates, RecurringService service, AppClock clock) {
        this.templates = templates;
        this.service = service;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run();
    }

    @Scheduled(cron = "0 */30 * * * *", zone = "${app.zone:Asia/Kolkata}")
    public void run() {
        LocalDate today = clock.today();
        for (Long id : templates.findDueIds(today)) {
            try {
                service.processDue(id, today); // one transaction per template
            } catch (RuntimeException e) {
                log.error("Failed to process recurring expense {}", id, e);
            }
        }
    }
}
