package com.moneyflowbackend.categoryboard.controller;

import com.moneyflowbackend.categoryboard.dto.CategoryBoardResponse;
import com.moneyflowbackend.categoryboard.service.CategoryBoardService;
import com.moneyflowbackend.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/category-board")
public class CategoryBoardController {
    private final CategoryBoardService categoryBoardService;

    public CategoryBoardController(CategoryBoardService categoryBoardService) {
        this.categoryBoardService = categoryBoardService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<CategoryBoardResponse>> getBoard(
            @PathVariable UUID workspaceId,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "true") boolean includeEmptyJars,
            @RequestParam(defaultValue = "true") boolean includeUncategorized,
            @RequestParam(defaultValue = "false") boolean includeStats,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String period) {
        CategoryBoardResponse res = categoryBoardService.getBoard(
                workspaceId,
                includeArchived,
                includeEmptyJars,
                includeUncategorized,
                includeStats,
                from,
                to,
                period,
                currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Category board loaded", res));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }
}
