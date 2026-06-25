package com.moneyflowbackend.category.controller;

import com.moneyflowbackend.category.dto.CategoryKeywordRequest;
import com.moneyflowbackend.category.dto.CategoryKeywordResponse;
import com.moneyflowbackend.category.service.CategoryKeywordService;
import com.moneyflowbackend.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/categories/{categoryId}/keywords")
public class CategoryKeywordController {

    private final CategoryKeywordService keywordService;

    public CategoryKeywordController(CategoryKeywordService keywordService) {
        this.keywordService = keywordService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryKeywordResponse>>> list(
            @PathVariable UUID workspaceId,
            @PathVariable UUID categoryId) {
        List<CategoryKeywordResponse> res = keywordService.list(workspaceId, categoryId, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Category keywords loaded", res));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CategoryKeywordResponse>> create(
            @PathVariable UUID workspaceId,
            @PathVariable UUID categoryId,
            @Valid @RequestBody CategoryKeywordRequest req) {
        CategoryKeywordResponse res = keywordService.create(workspaceId, categoryId, req, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Category keyword created", res));
    }

    @PutMapping("/{keywordId}")
    public ResponseEntity<ApiResponse<CategoryKeywordResponse>> update(
            @PathVariable UUID workspaceId,
            @PathVariable UUID categoryId,
            @PathVariable UUID keywordId,
            @Valid @RequestBody CategoryKeywordRequest req) {
        CategoryKeywordResponse res = keywordService.update(workspaceId, categoryId, keywordId, req, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Category keyword updated", res));
    }

    @DeleteMapping("/{keywordId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID workspaceId,
            @PathVariable UUID categoryId,
            @PathVariable UUID keywordId) {
        keywordService.delete(workspaceId, categoryId, keywordId, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Category keyword deleted", null));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }
}
