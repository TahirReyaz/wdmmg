package com.wdmmg.expense.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class CategoryDtos {
    private CategoryDtos() {}

    public record CategoryResponse(Long id, String name, String icon, String color, boolean active, int sortOrder) {
        public static CategoryResponse from(Category c) {
            return new CategoryResponse(c.getId(), c.getName(), c.getIcon(), c.getColor(), c.isActive(), c.getSortOrder());
        }
    }

    public record AdminCategoryResponse(Long id, String name, String icon, String color, boolean active,
                                        int sortOrder, long usageCount) {}

    public record CategoryRequest(
            @NotBlank @Size(max = 60) String name,
            @Size(max = 40) String icon,
            @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "must be a hex colour like #4c78a8") String color,
            Integer sortOrder,
            Boolean active) {}
}
