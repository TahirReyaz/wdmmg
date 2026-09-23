package com.tahir.finance.category.api;

import com.tahir.finance.auth.security.CurrentUser;
import com.tahir.finance.category.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categories;

    public CategoryController(CategoryService categories) {
        this.categories = categories;
    }

    @GetMapping
    public List<CategoryResponse> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return categories.list(CurrentUser.id(), includeArchived);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@Valid @RequestBody CategoryRequest request) {
        return categories.create(CurrentUser.id(), request);
    }

    @PatchMapping("/{categoryId}")
    public CategoryResponse update(@PathVariable UUID categoryId, @Valid @RequestBody CategoryRequest request) {
        return categories.update(CurrentUser.id(), categoryId, request);
    }

    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> delete(@PathVariable UUID categoryId,
                                       @RequestParam(required = false) UUID reassignTo) {
        categories.delete(CurrentUser.id(), categoryId, reassignTo);
        return ResponseEntity.noContent().build();
    }
}
