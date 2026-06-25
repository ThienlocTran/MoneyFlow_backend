package com.moneyflowbackend.category.controller;

import com.moneyflowbackend.category.dto.CategoryReorderRequest;
import com.moneyflowbackend.category.dto.CategoryRequest;
import com.moneyflowbackend.category.dto.CategoryResponse;
import com.moneyflowbackend.category.service.CategoryService;
import com.moneyflowbackend.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> list(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) UUID jarId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean archived,
            @RequestParam(required = false) Boolean quickAction,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        List<CategoryResponse> res = categoryService.list(
                workspaceId, type, jarId, active, archived, quickAction, includeInactive, includeArchived, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Categories loaded", res));
    }

    @GetMapping("/{categoryId}")
    public ResponseEntity<ApiResponse<CategoryResponse>> get(
            @PathVariable UUID workspaceId,
            @PathVariable UUID categoryId) {
        CategoryResponse res = categoryService.get(workspaceId, categoryId, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Category loaded", res));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> create(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody CategoryRequest req) {
        CategoryResponse res = categoryService.create(workspaceId, req, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Category created", res));
    }

    @PutMapping("/{categoryId}")
    public ResponseEntity<ApiResponse<CategoryResponse>> update(
            @PathVariable UUID workspaceId,
            @PathVariable UUID categoryId,
            @Valid @RequestBody CategoryRequest req) {
        CategoryResponse res = categoryService.update(workspaceId, categoryId, req, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Category updated", res));
    }

    @PatchMapping("/{categoryId}/status")
    public ResponseEntity<ApiResponse<Void>> setStatus(
            @PathVariable UUID workspaceId,
            @PathVariable UUID categoryId,
            @RequestParam boolean active) {
        categoryService.toggleStatus(workspaceId, categoryId, active, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Category status updated", null));
    }

    @PatchMapping("/{categoryId}/archive")
    public ResponseEntity<ApiResponse<Void>> setArchived(
            @PathVariable UUID workspaceId,
            @PathVariable UUID categoryId,
            @RequestParam boolean archived) {
        categoryService.toggleArchived(workspaceId, categoryId, archived, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Category archive status updated", null));
    }

    @PatchMapping("/{categoryId}/quick-action")
    public ResponseEntity<ApiResponse<Void>> setQuickAction(
            @PathVariable UUID workspaceId,
            @PathVariable UUID categoryId,
            @RequestParam boolean quickAction) {
        categoryService.toggleQuickAction(workspaceId, categoryId, quickAction, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Category quick action updated", null));
    }

    @PutMapping("/reorder")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> reorder(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody CategoryReorderRequest req) {
        List<CategoryResponse> res = categoryService.reorder(workspaceId, req, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Categories reordered", res));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }
}
