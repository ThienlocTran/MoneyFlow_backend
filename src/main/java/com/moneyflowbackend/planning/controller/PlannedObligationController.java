package com.moneyflowbackend.planning.controller;

import com.moneyflowbackend.dto.ApiResponse;
import com.moneyflowbackend.planning.dto.CancelPlannedObligationRequest;
import com.moneyflowbackend.planning.dto.LinkPlannedObligationTransactionRequest;
import com.moneyflowbackend.planning.dto.MarkPlannedObligationPaidRequest;
import com.moneyflowbackend.planning.dto.PlannedObligationListResponse;
import com.moneyflowbackend.planning.dto.PlannedObligationMarkPaidResponse;
import com.moneyflowbackend.planning.dto.PlannedObligationRequest;
import com.moneyflowbackend.planning.dto.PlannedObligationResponse;
import com.moneyflowbackend.planning.dto.PlannedObligationUpdateRequest;
import com.moneyflowbackend.planning.service.PlannedObligationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/planning/obligations")
public class PlannedObligationController {
    private final PlannedObligationService plannedObligationService;

    public PlannedObligationController(PlannedObligationService plannedObligationService) {
        this.plannedObligationService = plannedObligationService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PlannedObligationResponse>> create(
            @PathVariable UUID workspaceId,
            @RequestBody PlannedObligationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Planned obligation created",
                plannedObligationService.create(workspaceId, request, currentUserId())));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PlannedObligationListResponse>> list(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(defaultValue = "false") boolean includeCancelled) {
        return ResponseEntity.ok(ApiResponse.ok("Planned obligations loaded",
                plannedObligationService.list(workspaceId, from, to, status, priority, includeCancelled, currentUserId())));
    }

    @GetMapping("/{obligationId}")
    public ResponseEntity<ApiResponse<PlannedObligationResponse>> get(
            @PathVariable UUID workspaceId,
            @PathVariable UUID obligationId) {
        return ResponseEntity.ok(ApiResponse.ok("Planned obligation loaded",
                plannedObligationService.get(workspaceId, obligationId, currentUserId())));
    }

    @PatchMapping("/{obligationId}")
    public ResponseEntity<ApiResponse<PlannedObligationResponse>> update(
            @PathVariable UUID workspaceId,
            @PathVariable UUID obligationId,
            @RequestBody(required = false) PlannedObligationUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Planned obligation updated",
                plannedObligationService.update(workspaceId, obligationId, request, currentUserId())));
    }

    @PostMapping("/{obligationId}/cancel")
    public ResponseEntity<ApiResponse<PlannedObligationResponse>> cancel(
            @PathVariable UUID workspaceId,
            @PathVariable UUID obligationId,
            @RequestBody(required = false) CancelPlannedObligationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Planned obligation cancelled",
                plannedObligationService.cancel(workspaceId, obligationId, request, currentUserId())));
    }

    @PostMapping("/{obligationId}/link-transaction")
    public ResponseEntity<ApiResponse<PlannedObligationResponse>> linkTransaction(
            @PathVariable UUID workspaceId,
            @PathVariable UUID obligationId,
            @RequestBody(required = false) LinkPlannedObligationTransactionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Planned obligation linked to transaction",
                plannedObligationService.linkTransaction(workspaceId, obligationId, request, currentUserId())));
    }

    @PostMapping("/{obligationId}/mark-paid")
    public ResponseEntity<ApiResponse<PlannedObligationMarkPaidResponse>> markPaid(
            @PathVariable UUID workspaceId,
            @PathVariable UUID obligationId,
            @RequestBody(required = false) MarkPlannedObligationPaidRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Planned obligation marked paid",
                plannedObligationService.markPaid(workspaceId, obligationId, request, currentUserId())));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }
}
