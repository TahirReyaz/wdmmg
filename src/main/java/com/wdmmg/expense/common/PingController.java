package com.wdmmg.expense.common;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Keep-alive target for an external cron (e.g. cron-job.org) so the free Render instance
 * doesn't spin down. Deliberately trivial: no auth, no database, no logging, two bytes back.
 * Unlike /actuator/health it never touches Postgres, so a serverless database (Neon) can still
 * scale to zero between real requests. HEAD works too.
 */
@RestController
public class PingController {
    @GetMapping(value = "/ping", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body("ok");
    }
}
