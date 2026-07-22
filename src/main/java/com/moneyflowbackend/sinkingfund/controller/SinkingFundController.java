package com.moneyflowbackend.sinkingfund.controller;

import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.dto.ApiResponse;
import com.moneyflowbackend.sinkingfund.dto.SinkingFundAllocationPageResponse;
import com.moneyflowbackend.sinkingfund.dto.SinkingFundAllocationRequest;
import com.moneyflowbackend.sinkingfund.dto.SinkingFundAllocationResponse;
import com.moneyflowbackend.sinkingfund.dto.SinkingFundPageResponse;
import com.moneyflowbackend.sinkingfund.dto.SinkingFundRequest;
import com.moneyflowbackend.sinkingfund.dto.SinkingFundResponse;
import com.moneyflowbackend.sinkingfund.model.SinkingFundStatus;
import com.moneyflowbackend.sinkingfund.service.SinkingFundService;
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

import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/sinking-funds")
public class SinkingFundController {
    private final SinkingFundService sinkingFundService;

    public SinkingFundController(SinkingFundService sinkingFundService) {
        this.sinkingFundService = sinkingFundService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<SinkingFundPageResponse>> list(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        SinkingFundPageResponse response = sinkingFundService.list(workspaceId, parseStatus(status), page, size, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Sinking funds loaded", response));
    }

    @GetMapping("/{fundId}")
    public ResponseEntity<ApiResponse<SinkingFundResponse>> get(
            @PathVariable UUID workspaceId,
            @PathVariable UUID fundId) {
        SinkingFundResponse response = sinkingFundService.get(workspaceId, fundId, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Sinking fund loaded", response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SinkingFundResponse>> create(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody SinkingFundRequest request) {
        SinkingFundResponse response = sinkingFundService.create(workspaceId, request, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Sinking fund created", response));
    }

    @PutMapping("/{fundId}")
    public ResponseEntity<ApiResponse<SinkingFundResponse>> update(
            @PathVariable UUID workspaceId,
            @PathVariable UUID fundId,
            @Valid @RequestBody SinkingFundRequest request) {
        SinkingFundResponse response = sinkingFundService.update(workspaceId, fundId, request, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Sinking fund updated", response));
    }

    @GetMapping("/{fundId}/allocations")
    public ResponseEntity<ApiResponse<SinkingFundAllocationPageResponse>> history(
            @PathVariable UUID workspaceId,
            @PathVariable UUID fundId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        SinkingFundAllocationPageResponse response = sinkingFundService.history(workspaceId, fundId, page, size, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Sinking fund allocation history loaded", response));
    }

    @PostMapping("/{fundId}/allocations")
    public ResponseEntity<ApiResponse<SinkingFundAllocationResponse>> allocate(
            @PathVariable UUID workspaceId,
            @PathVariable UUID fundId,
            @Valid @RequestBody SinkingFundAllocationRequest request) {
        SinkingFundAllocationResponse response = sinkingFundService.allocate(workspaceId, fundId, request, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Sinking fund allocation recorded", response));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }

    private SinkingFundStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return SinkingFundStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("VALIDATION_ERROR", "Sinking fund status is invalid");
        }
    }
}
