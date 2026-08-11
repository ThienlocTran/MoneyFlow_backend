package com.moneyflowbackend.planning.controller;

import com.moneyflowbackend.dto.ApiResponse;
import com.moneyflowbackend.planning.dto.ReserveAllocationActionRequest;
import com.moneyflowbackend.planning.dto.ReserveAllocationListResponse;
import com.moneyflowbackend.planning.dto.ReserveAllocationRequest;
import com.moneyflowbackend.planning.dto.ReserveAllocationResponse;
import com.moneyflowbackend.planning.dto.ReserveAllocationUpdateRequest;
import com.moneyflowbackend.planning.service.ReserveAllocationService;
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
@RequestMapping("/api/workspaces/{workspaceId}/planning/reserves")
public class ReserveAllocationController {
    private final ReserveAllocationService reserveAllocationService;

    public ReserveAllocationController(ReserveAllocationService reserveAllocationService) {
        this.reserveAllocationService = reserveAllocationService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReserveAllocationResponse>> create(
            @PathVariable UUID workspaceId,
            @RequestBody ReserveAllocationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Reserve allocation created",
                reserveAllocationService.create(workspaceId, request, currentUserId())));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ReserveAllocationListResponse>> list(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String purposeType,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(required = false) LocalDate fromTargetDate,
            @RequestParam(required = false) LocalDate toTargetDate) {
        return ResponseEntity.ok(ApiResponse.ok("Reserve allocations loaded",
                reserveAllocationService.list(workspaceId, status, purposeType, includeInactive, fromTargetDate, toTargetDate, currentUserId())));
    }

    @GetMapping("/{reserveId}")
    public ResponseEntity<ApiResponse<ReserveAllocationResponse>> get(
            @PathVariable UUID workspaceId,
            @PathVariable UUID reserveId) {
        return ResponseEntity.ok(ApiResponse.ok("Reserve allocation loaded",
                reserveAllocationService.get(workspaceId, reserveId, currentUserId())));
    }

    @PatchMapping("/{reserveId}")
    public ResponseEntity<ApiResponse<ReserveAllocationResponse>> update(
            @PathVariable UUID workspaceId,
            @PathVariable UUID reserveId,
            @RequestBody(required = false) ReserveAllocationUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Reserve allocation updated",
                reserveAllocationService.update(workspaceId, reserveId, request, currentUserId())));
    }

    @PostMapping("/{reserveId}/release")
    public ResponseEntity<ApiResponse<ReserveAllocationResponse>> release(
            @PathVariable UUID workspaceId,
            @PathVariable UUID reserveId,
            @RequestBody(required = false) ReserveAllocationActionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Reserve allocation released",
                reserveAllocationService.release(workspaceId, reserveId, request, currentUserId())));
    }

    @PostMapping("/{reserveId}/cancel")
    public ResponseEntity<ApiResponse<ReserveAllocationResponse>> cancel(
            @PathVariable UUID workspaceId,
            @PathVariable UUID reserveId,
            @RequestBody(required = false) ReserveAllocationActionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Reserve allocation cancelled",
                reserveAllocationService.cancel(workspaceId, reserveId, request, currentUserId())));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }
}
