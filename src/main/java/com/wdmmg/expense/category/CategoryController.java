package com.wdmmg.expense.category;

import com.wdmmg.expense.category.CategoryDtos.CategoryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    /** Active expense types for pickers. */
    @GetMapping
    public List<CategoryResponse> list() {
        return service.listActive();
    }
}
