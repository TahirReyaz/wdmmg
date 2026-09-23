package com.tahir.finance.category.api;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CategoryRequest(
        @Size(min = 1, max = 60) String name,
        @Size(max = 40) String icon,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "must be a hex colour such as #3E5C76") String colorHex,
        UUID parentId,
        Short sortOrder,
        Boolean archived) {
}
