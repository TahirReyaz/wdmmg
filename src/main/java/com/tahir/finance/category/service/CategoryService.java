package com.tahir.finance.category.service;

import com.tahir.finance.category.api.CategoryRequest;
import com.tahir.finance.category.api.CategoryResponse;
import com.tahir.finance.category.domain.ExpenseCategory;
import com.tahir.finance.category.domain.ExpenseCategoryRepository;
import com.tahir.finance.common.Uuid7;
import com.tahir.finance.common.error.ApiException;
import com.tahir.finance.expense.domain.ExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CategoryService {

    /**
     * Seeded for every new account. Colours come from a single restrained
     * palette so the charts look deliberate out of the box rather than like a
     * random hue per slice.
     */
    private static final List<DefaultCategory> DEFAULTS = List.of(
            new DefaultCategory("Food & Dining", "utensils", "#B4472A"),
            new DefaultCategory("Groceries", "basket", "#7A6A3F"),
            new DefaultCategory("Transport", "car", "#3E5C76"),
            new DefaultCategory("Rent & Housing", "home", "#4A4E69"),
            new DefaultCategory("Utilities", "bolt", "#5C6B73"),
            new DefaultCategory("Health", "heart", "#8C5A67"),
            new DefaultCategory("Shopping", "bag", "#946846"),
            new DefaultCategory("Entertainment", "film", "#5E6E5E"),
            new DefaultCategory("Travel", "plane", "#3F6C63"),
            new DefaultCategory("Education", "book", "#55607A"),
            new DefaultCategory("Investments", "chart", "#2F5D50"),
            new DefaultCategory("Subscriptions", "repeat", "#6B5B7B"),
            new DefaultCategory("Gifts & Donations", "gift", "#8A5A44"),
            new DefaultCategory("Other", "dots", "#6E6E6E"));

    private final ExpenseCategoryRepository categories;
    private final ExpenseRepository expenses;

    public CategoryService(ExpenseCategoryRepository categories, ExpenseRepository expenses) {
        this.categories = categories;
        this.expenses = expenses;
    }

    /** Called once, inside the registration transaction. */
    @Transactional
    public void seedDefaultsFor(UUID userId) {
        short order = 0;
        for (DefaultCategory def : DEFAULTS) {
            ExpenseCategory category = new ExpenseCategory();
            category.setId(Uuid7.generate());
            category.setUserId(userId);
            category.setName(def.name());
            category.setIcon(def.icon());
            category.setColorHex(def.color());
            category.setSortOrder(order++);
            categories.save(category);
        }
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(UUID userId, boolean includeArchived) {
        return categories.findVisible(userId, includeArchived).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional
    public CategoryResponse create(UUID userId, CategoryRequest request) {
        if (categories.nameTaken(userId, request.name(), Uuid7.NIL)) {
            throw ApiException.conflict("CATEGORY_EXISTS",
                    "You already have a category called '" + request.name() + "'.");
        }

        ExpenseCategory category = new ExpenseCategory();
        category.setId(Uuid7.generate());
        category.setUserId(userId);
        category.setName(request.name().trim());
        category.setIcon(request.icon());
        category.setColorHex(request.colorHex());
        category.setParentId(request.parentId());
        category.setSortOrder(request.sortOrder() == null ? 100 : request.sortOrder());

        return CategoryResponse.from(categories.save(category));
    }

    @Transactional
    public CategoryResponse update(UUID userId, UUID categoryId, CategoryRequest request) {
        ExpenseCategory category = ownedOrThrow(userId, categoryId);

        if (request.name() != null && !request.name().isBlank()) {
            if (categories.nameTaken(userId, request.name(), categoryId)) {
                throw ApiException.conflict("CATEGORY_EXISTS",
                        "You already have a category called '" + request.name() + "'.");
            }
            category.setName(request.name().trim());
        }
        if (request.icon() != null) {
            category.setIcon(request.icon());
        }
        if (request.colorHex() != null) {
            category.setColorHex(request.colorHex());
        }
        if (request.sortOrder() != null) {
            category.setSortOrder(request.sortOrder());
        }
        if (request.archived() != null) {
            category.setArchived(request.archived());
        }

        return CategoryResponse.from(categories.save(category));
    }

    /**
     * Deleting a category that still has expenses would silently orphan them, so
     * the caller must say where those expenses go.
     */
    @Transactional
    public void delete(UUID userId, UUID categoryId, UUID reassignTo) {
        ExpenseCategory category = ownedOrThrow(userId, categoryId);

        long inUse = expenses.countByCategory(userId, categoryId);
        if (inUse > 0) {
            if (reassignTo == null) {
                throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "CATEGORY_IN_USE",
                        inUse + " expenses still use this category. "
                                + "Pass ?reassignTo={categoryId} to move them first.",
                        java.util.Map.of("expenseCount", inUse));
            }
            ownedOrThrow(userId, reassignTo);
            expenses.reassignCategory(userId, categoryId, reassignTo);
        }

        categories.delete(category);
    }

    private ExpenseCategory ownedOrThrow(UUID userId, UUID categoryId) {
        ExpenseCategory category = categories.findVisibleById(categoryId, userId)
                .orElseThrow(() -> ApiException.notFound("Category"));

        if (category.getUserId() == null) {
            throw ApiException.forbidden("SYSTEM_CATEGORY", "System categories cannot be changed.");
        }
        return category;
    }

    private record DefaultCategory(String name, String icon, String color) {
    }
}
