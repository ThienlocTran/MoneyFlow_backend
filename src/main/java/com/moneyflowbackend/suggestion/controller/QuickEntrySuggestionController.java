package com.moneyflowbackend.suggestion.controller;

import com.moneyflowbackend.dto.ApiResponse;
import com.moneyflowbackend.suggestion.dto.QuickEntrySuggestionRequest;
import com.moneyflowbackend.suggestion.dto.QuickEntrySuggestionResponse;
import com.moneyflowbackend.suggestion.service.QuickEntrySuggestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/suggestions")
public class QuickEntrySuggestionController {
    private final QuickEntrySuggestionService suggestionService;

    public QuickEntrySuggestionController(QuickEntrySuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    @PostMapping("/quick-entry")
    public ResponseEntity<ApiResponse<QuickEntrySuggestionResponse>> suggestQuickEntry(
            @PathVariable UUID workspaceId,
            @RequestBody(required = false) QuickEntrySuggestionRequest request) {
        QuickEntrySuggestionResponse response = suggestionService.suggest(workspaceId, request, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Quick entry suggestions loaded", response));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }
}
