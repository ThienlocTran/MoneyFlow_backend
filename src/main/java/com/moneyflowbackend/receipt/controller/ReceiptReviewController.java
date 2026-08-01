package com.moneyflowbackend.receipt.controller;

import com.moneyflowbackend.dto.ApiResponse;
import com.moneyflowbackend.receipt.dto.ReceiptReviewParseRequest;
import com.moneyflowbackend.receipt.dto.ReceiptReviewParseResponse;
import com.moneyflowbackend.receipt.service.ReceiptReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/receipt-review")
public class ReceiptReviewController {
    private final ReceiptReviewService receiptReviewService;

    public ReceiptReviewController(ReceiptReviewService receiptReviewService) {
        this.receiptReviewService = receiptReviewService;
    }

    @PostMapping("/parse")
    public ResponseEntity<ApiResponse<ReceiptReviewParseResponse>> parse(
            @PathVariable UUID workspaceId,
            @RequestBody ReceiptReviewParseRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Receipt review draft parsed", receiptReviewService.parse(workspaceId, req, currentUserId())));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }
}
