package com.moneyflowbackend.obligation.controller;

import com.moneyflowbackend.dto.ApiResponse;
import com.moneyflowbackend.obligation.dto.RecurringObligationPreviewRequest;
import com.moneyflowbackend.obligation.dto.RecurringObligationPreviewResponse;
import com.moneyflowbackend.obligation.dto.RecurringObligationTemplatePageResponse;
import com.moneyflowbackend.obligation.dto.RecurringObligationTemplateRequest;
import com.moneyflowbackend.obligation.dto.RecurringObligationTemplateResponse;
import com.moneyflowbackend.obligation.model.ObligationDirection;
import com.moneyflowbackend.obligation.model.RecurringObligationStatus;
import com.moneyflowbackend.obligation.service.RecurringObligationTemplateService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/recurring-obligations")
public class RecurringObligationTemplateController {
    private final RecurringObligationTemplateService templateService;

    public RecurringObligationTemplateController(RecurringObligationTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<RecurringObligationTemplatePageResponse>> list(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) RecurringObligationStatus status,
            @RequestParam(required = false) ObligationDirection direction,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        RecurringObligationTemplatePageResponse response = templateService.list(
                workspaceId, status, direction, search, includeArchived, page, size, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Recurring obligations loaded", response));
    }

    @GetMapping("/{templateId}")
    public ResponseEntity<ApiResponse<RecurringObligationTemplateResponse>> get(
            @PathVariable UUID workspaceId,
            @PathVariable UUID templateId) {
        RecurringObligationTemplateResponse response = templateService.get(workspaceId, templateId, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Recurring obligation loaded", response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RecurringObligationTemplateResponse>> create(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody RecurringObligationTemplateRequest request) {
        RecurringObligationTemplateResponse response = templateService.create(workspaceId, request, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Recurring obligation created", response));
    }

    @PutMapping("/{templateId}")
    public ResponseEntity<ApiResponse<RecurringObligationTemplateResponse>> update(
            @PathVariable UUID workspaceId,
            @PathVariable UUID templateId,
            @Valid @RequestBody RecurringObligationTemplateRequest request) {
        RecurringObligationTemplateResponse response = templateService.update(workspaceId, templateId, request, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Recurring obligation updated", response));
    }

    @PostMapping("/{templateId}/pause")
    public ResponseEntity<ApiResponse<RecurringObligationTemplateResponse>> pause(
            @PathVariable UUID workspaceId,
            @PathVariable UUID templateId) {
        RecurringObligationTemplateResponse response = templateService.pause(workspaceId, templateId, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Recurring obligation paused", response));
    }

    @PostMapping("/{templateId}/resume")
    public ResponseEntity<ApiResponse<RecurringObligationTemplateResponse>> resume(
            @PathVariable UUID workspaceId,
            @PathVariable UUID templateId) {
        RecurringObligationTemplateResponse response = templateService.resume(workspaceId, templateId, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Recurring obligation resumed", response));
    }

    @PostMapping("/{templateId}/archive")
    public ResponseEntity<ApiResponse<RecurringObligationTemplateResponse>> archive(
            @PathVariable UUID workspaceId,
            @PathVariable UUID templateId) {
        RecurringObligationTemplateResponse response = templateService.archive(workspaceId, templateId, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Recurring obligation archived", response));
    }

    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<RecurringObligationPreviewResponse>> preview(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody RecurringObligationPreviewRequest request) {
        RecurringObligationPreviewResponse response = templateService.preview(workspaceId, request, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Recurring obligation preview loaded", response));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }
}
