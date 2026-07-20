package com.moneyflowbackend.closing.controller;

import com.moneyflowbackend.closing.dto.CompleteDailyClosingRequest;
import com.moneyflowbackend.closing.dto.DailyClosingResponse;
import com.moneyflowbackend.closing.dto.ReconciliationAdjustmentRequest;
import com.moneyflowbackend.closing.dto.WalletSnapshotPageResponse;
import com.moneyflowbackend.closing.dto.WalletSnapshotRequest;
import com.moneyflowbackend.closing.service.DailyClosingService;
import com.moneyflowbackend.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}")
public class DailyClosingController {
    private final DailyClosingService dailyClosingService;

    public DailyClosingController(DailyClosingService dailyClosingService) {
        this.dailyClosingService = dailyClosingService;
    }

    @GetMapping("/daily-closings/{closingDate}")
    public ResponseEntity<ApiResponse<DailyClosingResponse>> getDailyClosing(
            @PathVariable UUID workspaceId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate closingDate) {
        DailyClosingResponse response = dailyClosingService.getDailyClosing(workspaceId, closingDate, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Daily closing loaded", response));
    }

    @PutMapping("/daily-closings/{closingDate}/wallets/{walletId}/snapshot")
    public ResponseEntity<ApiResponse<DailyClosingResponse>> saveWalletSnapshot(
            @PathVariable UUID workspaceId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate closingDate,
            @PathVariable UUID walletId,
            @Valid @RequestBody WalletSnapshotRequest request) {
        DailyClosingResponse response = dailyClosingService.saveWalletSnapshot(workspaceId, closingDate, walletId, request, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Wallet snapshot saved", response));
    }

    @PostMapping("/daily-closings/{closingDate}/complete")
    public ResponseEntity<ApiResponse<DailyClosingResponse>> completeDailyClosing(
            @PathVariable UUID workspaceId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate closingDate,
            @RequestBody(required = false) CompleteDailyClosingRequest request) {
        DailyClosingResponse response = dailyClosingService.completeDailyClosing(workspaceId, closingDate, request, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Daily closing completed", response));
    }

    @PostMapping("/wallet-snapshots/{snapshotId}/adjustment")
    public ResponseEntity<ApiResponse<DailyClosingResponse>> reconcileWalletSnapshot(
            @PathVariable UUID workspaceId,
            @PathVariable UUID snapshotId,
            @RequestBody ReconciliationAdjustmentRequest request) {
        DailyClosingResponse response = dailyClosingService.reconcileWalletSnapshot(workspaceId, snapshotId, request, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Wallet snapshot adjusted", response));
    }

    @GetMapping("/wallet-snapshots")
    public ResponseEntity<ApiResponse<WalletSnapshotPageResponse>> listSnapshotHistory(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) UUID walletId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        WalletSnapshotPageResponse response = dailyClosingService.listSnapshotHistory(
                workspaceId, walletId, dateFrom, dateTo, page, size, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Wallet snapshots loaded", response));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }
}
