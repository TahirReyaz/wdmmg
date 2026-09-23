package com.tahir.finance.category.api;

import com.tahir.finance.category.domain.ExpenseCategory;

import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        String icon,
        String colorHex,
        UUID parentId,
        short sortOrder,
        boolean archived,
        boolean system) {

    public static CategoryResponse from(ExpenseCategory category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getIcon(),
                category.getColorHex(),
                category.getParentId(),
                category.getSortOrder(),
                category.isArchived(),
                category.getUserId() == null);
    }
}
