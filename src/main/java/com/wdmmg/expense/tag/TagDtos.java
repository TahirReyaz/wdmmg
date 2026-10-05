package com.wdmmg.expense.tag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class TagDtos {
    private TagDtos() {}

    public record TagRequest(
            @NotBlank @Size(max = 60) String name,
            @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "must be a hex colour like #2a78d6") String color) {}

    /** Embedded in expenses. */
    public record TagRef(Long id, String name, String color) {
        public static TagRef from(Tag t) {
            return t == null ? null : new TagRef(t.getId(), t.getName(), t.getColor());
        }
    }

    /** A tag with what has been spent under it. Dates are null when it has no expenses yet. */
    public record TagResponse(Long id, String name, String color, long expenseCount, BigDecimal total,
                              LocalDate firstDate, LocalDate lastDate, Instant createdAt) {}

    public record Slice(String name, String color, long count, BigDecimal total) {}

    public record TagSummary(TagResponse tag, long days, BigDecimal perDay,
                             List<Slice> byCategory, List<Slice> byPaymentMethod) {}
}
