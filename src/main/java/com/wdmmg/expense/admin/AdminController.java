package com.wdmmg.expense.admin;

import com.wdmmg.expense.category.CategoryDtos.AdminCategoryResponse;
import com.wdmmg.expense.category.CategoryDtos.CategoryRequest;
import com.wdmmg.expense.category.CategoryService;
import com.wdmmg.expense.common.ApiException;
import com.wdmmg.expense.security.AuthUser;
import com.wdmmg.expense.user.Role;
import com.wdmmg.expense.user.User;
import com.wdmmg.expense.user.UserDtos.UserResponse;
import com.wdmmg.expense.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Admin-only endpoints (guarded by SecurityConfig: /api/admin/** requires ROLE_ADMIN). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final CategoryService categories;
    private final UserRepository users;

    public AdminController(CategoryService categories, UserRepository users) {
        this.categories = categories;
        this.users = users;
    }

    // ---- expense types ----
    @GetMapping("/categories")
    public List<AdminCategoryResponse> categories() {
        return categories.listAllWithUsage();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminCategoryResponse create(@Valid @RequestBody CategoryRequest req) {
        return categories.create(req);
    }

    @PutMapping("/categories/{id}")
    public AdminCategoryResponse update(@PathVariable Long id, @Valid @RequestBody CategoryRequest req) {
        return categories.update(id, req);
    }

    /** Deletes an unused category, or retires one that has history. Returns {"result": "DELETED"|"RETIRED"}. */
    @DeleteMapping("/categories/{id}")
    public Map<String, String> delete(@PathVariable Long id) {
        return Map.of("result", categories.remove(id));
    }

    // ---- users ----
    public record RoleRequest(@NotNull Role role) {}

    @GetMapping("/users")
    public List<UserResponse> users() {
        return users.findAllByOrderByCreatedAtDesc().stream().map(UserResponse::from).toList();
    }

    @PutMapping("/users/{id}/role")
    @Transactional
    public UserResponse setRole(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                @Valid @RequestBody RoleRequest req) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        if (u.getId().equals(me.id()) && req.role() != Role.ADMIN) {
            throw ApiException.badRequest("You cannot remove your own admin role");
        }
        u.setRole(req.role());
        return UserResponse.from(u);
    }
}
