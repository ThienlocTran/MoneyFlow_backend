package com.moneyflowbackend.obligation.controller;

import com.moneyflowbackend.dto.ApiResponse;
import com.moneyflowbackend.obligation.dto.ObligationOccurrencePageResponse;
import com.moneyflowbackend.obligation.dto.ObligationOccurrenceResponse;
import com.moneyflowbackend.obligation.dto.SkipOccurrenceRequest;
import com.moneyflowbackend.obligation.dto.SnoozeOccurrenceRequest;
import com.moneyflowbackend.obligation.model.ObligationOccurrenceStatus;
import com.moneyflowbackend.obligation.service.FinancialInboxService;
import com.moneyflowbackend.obligation.service.ObligationOccurrenceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}")
public class ObligationOccurrenceController {
    private final FinancialInboxService inboxService;
    private final ObligationOccurrenceService occurrenceService;

    public ObligationOccurrenceController(
            FinancialInboxService inboxService,
            ObligationOccurrenceService occurrenceService) {
        this.inboxService = inboxService;
        this.occurrenceService = occurrenceService;
    }

    @GetMapping("/recurring-obligations/{templateId}/occurrences")
    public ResponseEntity<ApiResponse<ObligationOccurrencePageResponse>> history(
            @PathVariable UUID workspaceId,
            @PathVariable UUID templateId,
            @RequestParam(required = false) ObligationOccurrenceStatus status,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ObligationOccurrencePageResponse response = inboxService.history(
                workspaceId,
                templateId,
                status,
                from,
                to,
                page,
                size,
                currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Obligation occurrences loaded", response));
    }

    @PostMapping("/obligation-occurrences/{occurrenceId}/skip")
    public ResponseEntity<ApiResponse<ObligationOccurrenceResponse>> skip(
            @PathVariable UUID workspaceId,
            @PathVariable UUID occurrenceId,
            @RequestBody(required = false) SkipOccurrenceRequest request) {
        ObligationOccurrenceResponse response = occurrenceService.skip(workspaceId, occurrenceId, request, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Obligation occurrence skipped", response));
    }

    @PostMapping("/obligation-occurrences/{occurrenceId}/snooze")
    public ResponseEntity<ApiResponse<ObligationOccurrenceResponse>> snooze(
            @PathVariable UUID workspaceId,
            @PathVariable UUID occurrenceId,
            @Valid @RequestBody SnoozeOccurrenceRequest request) {
        ObligationOccurrenceResponse response = occurrenceService.snooze(workspaceId, occurrenceId, request, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Obligation occurrence snoozed", response));
    }

    @PostMapping("/obligation-occurrences/{occurrenceId}/reopen")
    public ResponseEntity<ApiResponse<ObligationOccurrenceResponse>> reopen(
            @PathVariable UUID workspaceId,
            @PathVariable UUID occurrenceId) {
        ObligationOccurrenceResponse response = occurrenceService.reopen(workspaceId, occurrenceId, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Obligation occurrence reopened", response));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }
}
