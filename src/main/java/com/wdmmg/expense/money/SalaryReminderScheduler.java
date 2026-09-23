package com.wdmmg.expense.money;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 9 am on each user's salary day: a notification asking them to record it. */
@Component
public class SalaryReminderScheduler {
    private static final Logger log = LoggerFactory.getLogger(SalaryReminderScheduler.class);
    private final MoneyService money;

    public SalaryReminderScheduler(MoneyService money) {
        this.money = money;
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "${app.zone:Asia/Kolkata}")
    public void run() {
        try {
            money.sendSalaryReminders();
        } catch (RuntimeException e) {
            log.error("Salary reminders failed", e);
        }
    }
}
