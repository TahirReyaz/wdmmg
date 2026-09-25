package com.wdmmg.expense.verification;

/** Published inside the transaction that created the code; the email goes out after commit. */
public record VerificationCodeIssued(String email, String name, String code, int ttlMinutes) {}
