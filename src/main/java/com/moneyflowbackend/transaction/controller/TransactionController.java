package com.moneyflowbackend.transaction.controller;

import com.moneyflowbackend.dto.ApiResponse;
import com.moneyflowbackend.transaction.dto.TransactionPageResponse;
import com.moneyflowbackend.transaction.dto.TransactionRequest;
import com.moneyflowbackend.transaction.dto.TransactionResponse;
import com.moneyflowbackend.transaction.model.TransactionSourceType;
import com.moneyflowbackend.transaction.model.TransactionStatus;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.transaction.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<TransactionPageResponse>> list(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) UUID walletId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID jarId,
            @RequestParam(required = false) UUID attributedPersonId,
            @RequestParam(required = false) TransactionSourceType sourceType,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        UUID userId = currentUserId();
        LocalDate effectiveFrom = dateFrom != null ? dateFrom : from;
        LocalDate effectiveTo = dateTo != null ? dateTo : to;
        if (month != null && !month.isBlank()) {
            YearMonth yearMonth = YearMonth.parse(month.trim());
            effectiveFrom = yearMonth.atDay(1);
            effectiveTo = yearMonth.atEndOfMonth();
        }
        TransactionPageResponse res = transactionService.list(
                workspaceId,
                effectiveFrom,
                effectiveTo,
                type,
                status,
                walletId,
                categoryId,
                jarId,
                attributedPersonId,
                sourceType,
                search != null ? search : keyword,
                includeDeleted,
                page,
                size,
                sort,
                userId);
        return ResponseEntity.ok(ApiResponse.ok("Transactions loaded", res));
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<ApiResponse<TransactionResponse>> get(
            @PathVariable UUID workspaceId,
            @PathVariable UUID transactionId,
            @RequestParam(defaultValue = "false") boolean includeDeleted) {
        TransactionResponse res = transactionService.getDetails(workspaceId, transactionId, includeDeleted, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Transaction loaded", res));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TransactionResponse>> create(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody TransactionRequest req) {
        TransactionResponse res = transactionService.create(workspaceId, req, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Transaction created", res));
    }

    @PutMapping("/{transactionId}")
    public ResponseEntity<ApiResponse<TransactionResponse>> update(
            @PathVariable UUID workspaceId,
            @PathVariable UUID transactionId,
            @Valid @RequestBody TransactionRequest req) {
        TransactionResponse res = transactionService.update(workspaceId, transactionId, req, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Transaction updated", res));
    }

    @DeleteMapping("/{transactionId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID workspaceId,
            @PathVariable UUID transactionId) {
        transactionService.delete(workspaceId, transactionId, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Transaction deleted", null));
    }

    @PostMapping("/{transactionId}/restore")
    public ResponseEntity<ApiResponse<TransactionResponse>> restorePost(
            @PathVariable UUID workspaceId,
            @PathVariable UUID transactionId) {
        return restore(workspaceId, transactionId);
    }

    @PutMapping("/{transactionId}/restore")
    public ResponseEntity<ApiResponse<TransactionResponse>> restore(
            @PathVariable UUID workspaceId,
            @PathVariable UUID transactionId) {
        TransactionResponse res = transactionService.restore(workspaceId, transactionId, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Transaction restored", res));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }
}
