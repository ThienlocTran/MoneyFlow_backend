package com.moneyflowbackend.receipt.session;

import com.moneyflowbackend.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/receipt-sessions")
public class ReceiptSessionController {
    private final ReceiptSessionService receiptSessionService;

    public ReceiptSessionController(ReceiptSessionService receiptSessionService) {
        this.receiptSessionService = receiptSessionService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReceiptSessionDetailResponse>> create(
            @PathVariable UUID workspaceId,
            @RequestBody(required = false) ReceiptSessionCreateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Receipt session created", receiptSessionService.create(workspaceId, req, currentUserId())));
    }

    @PostMapping("/{sessionId}/image")
    public ResponseEntity<ApiResponse<ReceiptSessionDetailResponse>> uploadImage(
            @PathVariable UUID workspaceId,
            @PathVariable UUID sessionId,
            @RequestParam(required = false) MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok("Receipt image uploaded", receiptSessionService.uploadImage(workspaceId, sessionId, file, currentUserId())));
    }

    @PostMapping("/{sessionId}/ocr")
    public ResponseEntity<ApiResponse<ReceiptSessionDetailResponse>> runOcr(
            @PathVariable UUID workspaceId,
            @PathVariable UUID sessionId) {
        return ResponseEntity.ok(ApiResponse.ok("Receipt OCR completed", receiptSessionService.runOcr(workspaceId, sessionId, currentUserId())));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<ReceiptSessionDetailResponse>> get(
            @PathVariable UUID workspaceId,
            @PathVariable UUID sessionId) {
        return ResponseEntity.ok(ApiResponse.ok("Receipt session loaded", receiptSessionService.get(workspaceId, sessionId, currentUserId())));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }
}
