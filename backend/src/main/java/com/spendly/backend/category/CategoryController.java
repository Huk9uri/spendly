package com.spendly.backend.category;

import com.spendly.backend.auth.SessionUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    private final CategoryService service;

    public CategoryController(CategoryService service) { this.service = service; }

    @GetMapping
    public List<CategoryResponse> list(@AuthenticationPrincipal SessionUser user,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.list(user.getId(), includeArchived);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@AuthenticationPrincipal SessionUser user,
            @Valid @RequestBody CreateCategoryRequest request) {
        return service.create(user.getId(), request);
    }

    @PatchMapping("/{id}")
    public CategoryResponse rename(@AuthenticationPrincipal SessionUser user, @PathVariable UUID id,
            @Valid @RequestBody RenameCategoryRequest request) {
        return service.rename(user.getId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@AuthenticationPrincipal SessionUser user, @PathVariable UUID id) {
        service.archive(user.getId(), id);
    }
}
