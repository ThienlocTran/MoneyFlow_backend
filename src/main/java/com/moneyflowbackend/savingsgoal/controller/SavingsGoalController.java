package com.moneyflowbackend.savingsgoal.controller;

import com.moneyflowbackend.dto.ApiResponse;
import com.moneyflowbackend.savingsgoal.dto.SavingsGoalPageResponse;
import com.moneyflowbackend.savingsgoal.dto.SavingsGoalRequest;
import com.moneyflowbackend.savingsgoal.dto.SavingsGoalResponse;
import com.moneyflowbackend.savingsgoal.dto.SavingsGoalStatusRequest;
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

    @GetMapping("/{goalId}")
    public ResponseEntity<ApiResponse<SavingsGoalResponse>> get(
            @PathVariable UUID workspaceId,
            @PathVariable UUID goalId) {
        SavingsGoalResponse response = goalService.get(workspaceId, goalId, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Savings goal loaded", response));
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

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }
}
