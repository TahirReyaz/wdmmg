package com.wdmmg.expense.verification;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.verification")
public record VerificationProperties(int codeTtlMinutes, int maxAttempts, int resendCooldownSeconds, int maxCodesPerHour) {}
