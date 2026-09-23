package com.wdmmg.expense.common;

import com.wdmmg.expense.security.AppProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/** Single source of "today" in the app's configured timezone (app.zone). */
@Component
public class AppClock {
    private final ZoneId zone;

    public AppClock(AppProperties props) {
        this.zone = ZoneId.of(props.zone() == null || props.zone().isBlank() ? "Asia/Kolkata" : props.zone());
    }

    public LocalDate today() {
        return LocalDate.now(zone);
    }

    public ZoneId zone() {
        return zone;
    }
}
