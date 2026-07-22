package com.moneyflowbackend.emergencyfund.controller;

import com.moneyflowbackend.dto.ApiResponse;
import com.moneyflowbackend.emergencyfund.dto.EmergencyFundPlanRequest;
import com.moneyflowbackend.emergencyfund.dto.EmergencyFundPlanResponse;
import com.moneyflowbackend.emergencyfund.dto.EmergencyFundPlanStatusRequest;
import com.moneyflowbackend.emergencyfund.service.EmergencyFundService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/emergency-fund")
public class EmergencyFundController {
    private final EmergencyFundService emergencyFundService;

    public EmergencyFundController(EmergencyFundService emergencyFundService) {
        this.emergencyFundService = emergencyFundService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<EmergencyFundPlanResponse>> get(@PathVariable UUID workspaceId) {
        EmergencyFundPlanResponse response = emergencyFundService.get(workspaceId, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Emergency fund loaded", response));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<EmergencyFundPlanResponse>> put(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody EmergencyFundPlanRequest request) {
        EmergencyFundPlanResponse response = emergencyFundService.put(workspaceId, request, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Emergency fund saved", response));
    }

    @PatchMapping("/status")
    public ResponseEntity<ApiResponse<EmergencyFundPlanResponse>> updateStatus(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody EmergencyFundPlanStatusRequest request) {
        EmergencyFundPlanResponse response = emergencyFundService.updateStatus(
                workspaceId, request == null ? null : request.getPlanStatus(), currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Emergency fund status updated", response));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }
}
