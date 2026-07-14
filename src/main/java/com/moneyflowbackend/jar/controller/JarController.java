package com.moneyflowbackend.jar.controller;

import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.dto.ApiResponse;
import com.moneyflowbackend.jar.dto.JarAllocationRequest;
import com.moneyflowbackend.jar.dto.JarListResponse;
import com.moneyflowbackend.jar.dto.JarMonthlyDetailResponse;
import com.moneyflowbackend.jar.dto.JarMonthlySummaryResponse;
import com.moneyflowbackend.jar.dto.JarReorderRequest;
import com.moneyflowbackend.jar.dto.JarRequest;
import com.moneyflowbackend.jar.dto.JarResponse;
import com.moneyflowbackend.jar.service.JarService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/jars")
public class JarController {

    private final JarService jarService;

    public JarController(JarService jarService) {
        this.jarService = jarService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<JarListResponse>> list(
            @PathVariable UUID workspaceId,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        JarListResponse res = jarService.list(workspaceId, includeInactive, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Jars loaded", res));
    }

    @GetMapping("/{jarId}")
    public ResponseEntity<ApiResponse<JarResponse>> get(
            @PathVariable UUID workspaceId,
            @PathVariable UUID jarId) {
        JarResponse res = jarService.get(workspaceId, jarId, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Jar loaded", res));
    }

    @GetMapping("/{jarId}/monthly-detail")
    public ResponseEntity<ApiResponse<JarMonthlyDetailResponse>> monthlyDetail(
            @PathVariable UUID workspaceId,
            @PathVariable UUID jarId,
            @RequestParam String month) {
        JarMonthlyDetailResponse res = jarService.monthlyDetail(workspaceId, jarId, month, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Jar monthly detail loaded", res));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<JarResponse>> create(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody JarRequest req) {
        JarResponse res = jarService.create(workspaceId, req, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Jar created", res));
    }

    @PutMapping("/{jarId}")
    public ResponseEntity<ApiResponse<JarResponse>> update(
            @PathVariable UUID workspaceId,
            @PathVariable UUID jarId,
            @Valid @RequestBody JarRequest req) {
        JarResponse res = jarService.update(workspaceId, jarId, req, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Jar updated", res));
    }

    @PatchMapping("/{jarId}/status")
    public ResponseEntity<ApiResponse<Void>> setStatus(
            @PathVariable UUID workspaceId,
            @PathVariable UUID jarId,
            @RequestParam(required = false) Boolean active,
            @RequestBody(required = false) Map<String, Boolean> body) {
        Boolean requestedActive = active != null ? active : body == null ? null : body.get("active");
        if (requestedActive == null) {
            throw new BusinessException("VALIDATION_ERROR", "Jar active status is required");
        }
        jarService.toggleStatus(workspaceId, jarId, requestedActive, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Jar status updated", null));
    }

    @PutMapping("/{jarId}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(
            @PathVariable UUID workspaceId,
            @PathVariable UUID jarId) {
        jarService.toggleStatus(workspaceId, jarId, true, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Jar activated", null));
    }

    @PutMapping("/{jarId}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(
            @PathVariable UUID workspaceId,
            @PathVariable UUID jarId) {
        jarService.toggleStatus(workspaceId, jarId, false, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Jar deactivated", null));
    }

    @DeleteMapping("/{jarId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID workspaceId,
            @PathVariable UUID jarId) {
        jarService.delete(workspaceId, jarId, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Jar deleted", null));
    }

    @PutMapping("/reorder")
    public ResponseEntity<ApiResponse<JarListResponse>> reorder(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody JarReorderRequest req) {
        JarListResponse res = jarService.reorder(workspaceId, req, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Jars reordered", res));
    }

    @PutMapping("/allocations")
    public ResponseEntity<ApiResponse<JarListResponse>> updateAllocations(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody JarAllocationRequest req) {
        JarListResponse res = jarService.updateAllocations(workspaceId, req, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Jar allocations updated", res));
    }

    @GetMapping("/percentage-summary")
    public ResponseEntity<ApiResponse<JarListResponse>> percentageSummary(@PathVariable UUID workspaceId) {
        JarListResponse res = jarService.list(workspaceId, true, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Jar percentage summary loaded", res));
    }

    @GetMapping("/monthly-summary")
    public ResponseEntity<ApiResponse<JarMonthlySummaryResponse>> monthlySummary(
            @PathVariable UUID workspaceId,
            @RequestParam String month) {
        JarMonthlySummaryResponse res = jarService.monthlySummary(workspaceId, month, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Jar monthly summary loaded", res));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }
}
