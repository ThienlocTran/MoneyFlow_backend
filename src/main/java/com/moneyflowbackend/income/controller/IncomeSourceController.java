package com.moneyflowbackend.income.controller;

import com.moneyflowbackend.dto.ApiResponse;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.income.dto.IncomeSourceRequest;
import com.moneyflowbackend.income.dto.IncomeSourceResponse;
import com.moneyflowbackend.income.model.IncomeSourceStatus;
import com.moneyflowbackend.income.service.IncomeSourceService;
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

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/income-sources")
public class IncomeSourceController {
    private final IncomeSourceService incomeSourceService;

    public IncomeSourceController(IncomeSourceService incomeSourceService) {
        this.incomeSourceService = incomeSourceService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<IncomeSourceResponse>>> list(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        List<IncomeSourceResponse> response = incomeSourceService.list(workspaceId, parseStatus(status), search, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Income sources loaded", response));
    }

    @GetMapping("/{incomeSourceId}")
    public ResponseEntity<ApiResponse<IncomeSourceResponse>> get(
            @PathVariable UUID workspaceId,
            @PathVariable UUID incomeSourceId) {
        IncomeSourceResponse response = incomeSourceService.get(workspaceId, incomeSourceId, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Income source loaded", response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<IncomeSourceResponse>> create(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody IncomeSourceRequest request) {
        IncomeSourceResponse response = incomeSourceService.create(workspaceId, request, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Income source created", response));
    }

    @PutMapping("/{incomeSourceId}")
    public ResponseEntity<ApiResponse<IncomeSourceResponse>> update(
            @PathVariable UUID workspaceId,
            @PathVariable UUID incomeSourceId,
            @Valid @RequestBody IncomeSourceRequest request) {
        IncomeSourceResponse response = incomeSourceService.update(workspaceId, incomeSourceId, request, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Income source updated", response));
    }

    @PostMapping("/{incomeSourceId}/archive")
    public ResponseEntity<ApiResponse<IncomeSourceResponse>> archive(
            @PathVariable UUID workspaceId,
            @PathVariable UUID incomeSourceId) {
        IncomeSourceResponse response = incomeSourceService.archive(workspaceId, incomeSourceId, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Income source archived", response));
    }

    @PostMapping("/{incomeSourceId}/restore")
    public ResponseEntity<ApiResponse<IncomeSourceResponse>> restore(
            @PathVariable UUID workspaceId,
            @PathVariable UUID incomeSourceId) {
        IncomeSourceResponse response = incomeSourceService.restore(workspaceId, incomeSourceId, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Income source restored", response));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }

    private IncomeSourceStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return IncomeSourceStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("VALIDATION_ERROR", "Income source status is invalid");
        }
    }
}
