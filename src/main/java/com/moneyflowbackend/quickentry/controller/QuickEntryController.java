package com.moneyflowbackend.quickentry.controller;

import com.moneyflowbackend.dto.ApiResponse;
import com.moneyflowbackend.quickentry.dto.QuickEntryButtonRequest;
import com.moneyflowbackend.quickentry.dto.QuickEntryConfirmRequest;
import com.moneyflowbackend.quickentry.dto.QuickEntryOptionsResponse;
import com.moneyflowbackend.quickentry.dto.QuickEntryParseRequest;
import com.moneyflowbackend.quickentry.dto.QuickEntryPreviewResponse;
import com.moneyflowbackend.quickentry.service.QuickEntryService;
import com.moneyflowbackend.transaction.dto.TransactionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/quick-entry")
public class QuickEntryController {
    private final QuickEntryService quickEntryService;

    public QuickEntryController(QuickEntryService quickEntryService) {
        this.quickEntryService = quickEntryService;
    }

    @GetMapping("/options")
    public ResponseEntity<ApiResponse<QuickEntryOptionsResponse>> options(@PathVariable UUID workspaceId) {
        QuickEntryOptionsResponse res = quickEntryService.options(workspaceId, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Quick entry options loaded", res));
    }

    @PostMapping("/parse")
    public ResponseEntity<ApiResponse<QuickEntryPreviewResponse>> parse(
            @PathVariable UUID workspaceId,
            @RequestBody QuickEntryParseRequest req) {
        QuickEntryPreviewResponse res = quickEntryService.parse(workspaceId, req == null ? null : req.getText(), currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Quick entry parsed", res));
    }

    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<TransactionResponse>> confirm(
            @PathVariable UUID workspaceId,
            @RequestBody QuickEntryConfirmRequest req) {
        TransactionResponse res = quickEntryService.confirm(workspaceId, req, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Quick entry transaction created", res));
    }

    @PostMapping("/confirm-voice")
    public ResponseEntity<ApiResponse<TransactionResponse>> confirmVoice(
            @PathVariable UUID workspaceId,
            @RequestBody QuickEntryConfirmRequest req) {
        TransactionResponse res = quickEntryService.confirmVoice(workspaceId, req, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Voice transaction created", res));
    }

    @PostMapping("/button")
    public ResponseEntity<ApiResponse<TransactionResponse>> button(
            @PathVariable UUID workspaceId,
            @RequestBody QuickEntryButtonRequest req) {
        TransactionResponse res = quickEntryService.button(workspaceId, req, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Quick button transaction created", res));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }
}
