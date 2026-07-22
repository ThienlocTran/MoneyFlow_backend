package com.moneyflowbackend.savingsgoal.controller;

import com.moneyflowbackend.dto.ApiResponse;
import com.moneyflowbackend.savingsgoal.dto.SavingsGoalLedgerEntryResponse;
import com.moneyflowbackend.savingsgoal.dto.SavingsGoalLedgerPageResponse;
import com.moneyflowbackend.savingsgoal.dto.SavingsGoalLedgerRequest;
import com.moneyflowbackend.savingsgoal.dto.SavingsGoalPageResponse;
import com.moneyflowbackend.savingsgoal.dto.SavingsGoalRequest;
import com.moneyflowbackend.savingsgoal.dto.SavingsGoalResponse;
import com.moneyflowbackend.savingsgoal.dto.SavingsGoalStatusRequest;
import com.moneyflowbackend.savingsgoal.dto.SavingsGoalSummaryItemResponse;
import com.moneyflowbackend.savingsgoal.dto.SavingsGoalSummaryListResponse;
import com.moneyflowbackend.savingsgoal.dto.SavingsGoalWorkspaceSummaryResponse;
import com.moneyflowbackend.savingsgoal.model.SavingsGoalStatus;
import com.moneyflowbackend.savingsgoal.service.SavingsGoalService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/savings-goals")
public class SavingsGoalController {
    private final SavingsGoalService goalService;

    public SavingsGoalController(SavingsGoalService goalService) {
        this.goalService = goalService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<SavingsGoalPageResponse>> list(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) SavingsGoalStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        SavingsGoalPageResponse response = goalService.list(
                workspaceId, status, search, includeArchived, page, size, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Savings goals loaded", response));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<SavingsGoalWorkspaceSummaryResponse>> workspaceSummary(
            @PathVariable UUID workspaceId,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        SavingsGoalWorkspaceSummaryResponse response = goalService.workspaceSummary(workspaceId, includeArchived, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Savings goals summary loaded", response));
    }

    @GetMapping("/summaries")
    public ResponseEntity<ApiResponse<SavingsGoalSummaryListResponse>> summaries(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) SavingsGoalStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        SavingsGoalSummaryListResponse response = goalService.summaries(
                workspaceId, status, search, includeArchived, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Savings goal summaries loaded", response));
    }

    @GetMapping("/{goalId}")
    public ResponseEntity<ApiResponse<SavingsGoalResponse>> get(
            @PathVariable UUID workspaceId,
            @PathVariable UUID goalId) {
        SavingsGoalResponse response = goalService.get(workspaceId, goalId, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Savings goal loaded", response));
    }

    @GetMapping("/{goalId}/summary")
    public ResponseEntity<ApiResponse<SavingsGoalSummaryItemResponse>> summary(
            @PathVariable UUID workspaceId,
            @PathVariable UUID goalId) {
        SavingsGoalSummaryItemResponse response = goalService.summary(workspaceId, goalId, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Savings goal summary loaded", response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SavingsGoalResponse>> create(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody SavingsGoalRequest request) {
        SavingsGoalResponse response = goalService.create(workspaceId, request, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Savings goal created", response));
    }

    @PutMapping("/{goalId}")
    public ResponseEntity<ApiResponse<SavingsGoalResponse>> update(
            @PathVariable UUID workspaceId,
            @PathVariable UUID goalId,
            @Valid @RequestBody SavingsGoalRequest request) {
        SavingsGoalResponse response = goalService.update(workspaceId, goalId, request, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Savings goal updated", response));
    }

    @PatchMapping("/{goalId}/status")
    public ResponseEntity<ApiResponse<SavingsGoalResponse>> updateStatus(
            @PathVariable UUID workspaceId,
            @PathVariable UUID goalId,
            @Valid @RequestBody SavingsGoalStatusRequest request) {
        SavingsGoalResponse response = goalService.updateStatus(
                workspaceId, goalId, request == null ? null : request.getStatus(), currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Savings goal status updated", response));
    }

    @PostMapping("/{goalId}/archive")
    public ResponseEntity<ApiResponse<SavingsGoalResponse>> archive(
            @PathVariable UUID workspaceId,
            @PathVariable UUID goalId) {
        SavingsGoalResponse response = goalService.archive(workspaceId, goalId, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Savings goal archived", response));
    }

    @GetMapping("/{goalId}/ledger")
    public ResponseEntity<ApiResponse<SavingsGoalLedgerPageResponse>> ledger(
            @PathVariable UUID workspaceId,
            @PathVariable UUID goalId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        SavingsGoalLedgerPageResponse response = goalService.ledger(workspaceId, goalId, page, size, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Savings goal ledger loaded", response));
    }

    @PostMapping("/{goalId}/contributions")
    public ResponseEntity<ApiResponse<SavingsGoalLedgerEntryResponse>> contribute(
            @PathVariable UUID workspaceId,
            @PathVariable UUID goalId,
            @Valid @RequestBody SavingsGoalLedgerRequest request) {
        SavingsGoalLedgerEntryResponse response = goalService.contribute(workspaceId, goalId, request, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Savings goal contribution recorded", response));
    }

    @PostMapping("/{goalId}/releases")
    public ResponseEntity<ApiResponse<SavingsGoalLedgerEntryResponse>> release(
            @PathVariable UUID workspaceId,
            @PathVariable UUID goalId,
            @Valid @RequestBody SavingsGoalLedgerRequest request) {
        SavingsGoalLedgerEntryResponse response = goalService.release(workspaceId, goalId, request, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Savings goal release recorded", response));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }
}
