package com.wdmmg.expense.category;

import com.wdmmg.expense.category.CategoryDtos.*;
import com.wdmmg.expense.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {
    private final CategoryRepository repo;

    public CategoryService(CategoryRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listActive() {
        return repo.findByActiveTrueOrderBySortOrderAscNameAsc().stream().map(CategoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AdminCategoryResponse> listAllWithUsage() {
        return repo.findAllByOrderBySortOrderAscNameAsc().stream().map(this::toAdmin).toList();
    }

    /** Resolves a category for use on a new/updated expense. Must exist and be active. */
    @Transactional(readOnly = true)
    public Category requireUsable(Long id) {
        Category c = repo.findById(id).orElseThrow(() -> ApiException.notFound("Category"));
        if (!c.isActive()) throw ApiException.badRequest("Category '" + c.getName() + "' has been retired");
        return c;
    }

    @Transactional
    public AdminCategoryResponse create(CategoryRequest req) {
        String name = req.name().trim();
        var existing = repo.findByNameIgnoreCase(name);
        if (existing.isPresent()) {
            Category c = existing.get();
            if (c.isActive()) throw ApiException.conflict("Category '" + name + "' already exists");
            // Re-adding a retired category simply restores it.
            c.setActive(true);
            apply(c, req);
            return toAdmin(c);
        }
        Category c = new Category();
        c.setName(name);
        apply(c, req);
        repo.save(c);
        return toAdmin(c);
    }

    @Transactional
    public AdminCategoryResponse update(Long id, CategoryRequest req) {
        Category c = repo.findById(id).orElseThrow(() -> ApiException.notFound("Category"));
        String name = req.name().trim();
        repo.findByNameIgnoreCase(name).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw ApiException.conflict("Category '" + name + "' already exists");
        });
        c.setName(name);
        apply(c, req);
        if (req.active() != null) c.setActive(req.active());
        return toAdmin(c);
    }

    /**
     * Removes a category. If any expense still references it, it is retired (hidden from pickers but kept
     * for history & analytics). Otherwise it is deleted permanently.
     */
    @Transactional
    public String remove(Long id) {
        Category c = repo.findById(id).orElseThrow(() -> ApiException.notFound("Category"));
        if (usage(c.getId()) > 0) {
            c.setActive(false);
            return "RETIRED";
        }
        repo.delete(c);
        return "DELETED";
    }

    private void apply(Category c, CategoryRequest req) {
        if (req.icon() != null) c.setIcon(req.icon().isBlank() ? null : req.icon().trim());
        if (req.color() != null) c.setColor(req.color().toLowerCase());
        if (req.sortOrder() != null) c.setSortOrder(req.sortOrder());
    }

    private long usage(Long id) {
        return repo.countPersonalUsage(id) + repo.countGroupUsage(id);
    }

    private AdminCategoryResponse toAdmin(Category c) {
        return new AdminCategoryResponse(c.getId(), c.getName(), c.getIcon(), c.getColor(), c.isActive(),
                c.getSortOrder(), c.getId() == null ? 0 : usage(c.getId()));
    }
}
