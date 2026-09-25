package com.wdmmg.expense.ai;

import java.time.LocalDate;
import java.util.List;

/**
 * Everything a model needs to turn "I had an ice cream for 30rs" into expenses.
 *
 * @param today         resolves "yesterday", "last Friday" …
 * @param categories    the only category names a draft may use
 * @param currency      ISO code amounts are assumed to be in
 */
public record InterpretationRequest(Long userId, String subject, String text, LocalDate today, List<String> categories,
                                    String currency) {}
