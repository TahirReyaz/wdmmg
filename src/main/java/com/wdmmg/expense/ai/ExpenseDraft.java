package com.wdmmg.expense.ai;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One expense proposed from free text, before anything is saved.
 *
 * @param category      one of the names offered in the request
 * @param paymentMethod a PaymentMethod name, or null if not mentioned
 * @param currency      ISO code the amount is in, as written or implied
 * @param confidence    0–1
 */
public record ExpenseDraft(String name, BigDecimal amount, String currency, LocalDate date, String category,
                           String paymentMethod, String notes, double confidence) {}
