package com.moneyflowbackend.planning.controller;

import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.dto.ApiResponse;
import com.moneyflowbackend.planning.dto.ActuallySpendableResponse;
import com.moneyflowbackend.planning.dto.PlanningPreferenceRequest;
import com.moneyflowbackend.planning.dto.PlanningPreferenceResponse;
import com.moneyflowbackend.planning.model.PlanningHorizon;
import com.moneyflowbackend.planning.service.PlanningPreferenceService;
import com.moneyflowbackend.planning.service.PlanningService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/planning")
public class PlanningController {
    private final PlanningService planningService;
    private final PlanningPreferenceService preferenceService;

    public PlanningController(PlanningService planningService, PlanningPreferenceService preferenceService) {
        this.planningService = planningService;
        this.preferenceService = preferenceService;
    }

    @GetMapping("/actually-spendable")
    public ResponseEntity<ApiResponse<ActuallySpendableResponse>> actuallySpendable(
            @PathVariable UUID workspaceId,
            @RequestParam(defaultValue = "CURRENT_MONTH") String horizon,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) List<UUID> walletIds) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Planning loaded",
                planningService.actuallySpendable(workspaceId, currentUserId(), parseHorizon(horizon), parseDate(from), parseDate(to), walletIds)));
    }

    @GetMapping("/preferences")
    public ResponseEntity<ApiResponse<PlanningPreferenceResponse>> getPreferences(@PathVariable UUID workspaceId) {
        return ResponseEntity.ok(ApiResponse.ok("Planning preferences loaded", preferenceService.get(workspaceId, currentUserId())));
    }

    @PutMapping("/preferences")
    public ResponseEntity<ApiResponse<PlanningPreferenceResponse>> putPreferences(
            @PathVariable UUID workspaceId,
            @org.springframework.web.bind.annotation.RequestBody PlanningPreferenceRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Planning preferences saved", preferenceService.put(workspaceId, req, currentUserId())));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }

    private PlanningHorizon parseHorizon(String raw) {
        try {
            return PlanningHorizon.valueOf((raw == null || raw.isBlank() ? "CURRENT_MONTH" : raw.trim().toUpperCase()));
        } catch (Exception ex) {
            throw new BusinessException("INVALID_PLANNING_HORIZON", "Planning horizon is invalid");
        }
    }

    private LocalDate parseDate(String raw) {
        try {
            return raw == null || raw.isBlank() ? null : LocalDate.parse(raw);
        } catch (Exception ex) {
            throw new BusinessException("INVALID_PLANNING_DATE", "Planning date must use YYYY-MM-DD");
        }
    }
}
