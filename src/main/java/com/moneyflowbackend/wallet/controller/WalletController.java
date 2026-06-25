package com.moneyflowbackend.wallet.controller;

import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.dto.ApiResponse;
import com.moneyflowbackend.wallet.dto.*;
import com.moneyflowbackend.wallet.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<WalletResponse>>> list(@PathVariable UUID workspaceId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = UUID.fromString(auth.getName());
        List<WalletResponse> res = walletService.list(workspaceId, userId);
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách ví thành công", res));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<WalletSummaryResponse>> summary(@PathVariable UUID workspaceId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = UUID.fromString(auth.getName());
        WalletSummaryResponse res = walletService.summary(workspaceId, userId);
        return ResponseEntity.ok(ApiResponse.ok("Wallet summary loaded", res));
    }

    @GetMapping("/{walletId}")
    public ResponseEntity<ApiResponse<WalletResponse>> get(
            @PathVariable UUID workspaceId,
            @PathVariable UUID walletId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = UUID.fromString(auth.getName());
        WalletResponse res = walletService.get(workspaceId, walletId, userId);
        return ResponseEntity.ok(ApiResponse.ok("Wallet loaded", res));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WalletResponse>> create(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody WalletRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = UUID.fromString(auth.getName());
        WalletResponse res = walletService.create(workspaceId, req, userId);
        return ResponseEntity.ok(ApiResponse.ok("Tạo ví thành công", res));
    }

    @PutMapping("/{walletId}")
    public ResponseEntity<ApiResponse<WalletResponse>> update(
            @PathVariable UUID workspaceId,
            @PathVariable UUID walletId,
            @Valid @RequestBody WalletRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = UUID.fromString(auth.getName());
        WalletResponse res = walletService.update(workspaceId, walletId, req, userId);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật ví thành công", res));
    }

    @PatchMapping("/{walletId}/default")
    public ResponseEntity<ApiResponse<Void>> setDefault(
            @PathVariable UUID workspaceId,
            @PathVariable UUID walletId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = UUID.fromString(auth.getName());
        walletService.setDefault(workspaceId, walletId, userId);
        return ResponseEntity.ok(ApiResponse.ok("Đặt làm ví mặc định thành công", null));
    }

    @PatchMapping("/{walletId}/status")
    public ResponseEntity<ApiResponse<Void>> setStatus(
            @PathVariable UUID workspaceId,
            @PathVariable UUID walletId,
            @RequestParam(required = false) Boolean active,
            @RequestBody(required = false) Map<String, Boolean> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = UUID.fromString(auth.getName());
        Boolean requestedActive = active != null ? active : body == null ? null : body.get("active");
        if (requestedActive == null) {
            throw new BusinessException("VALIDATION_ERROR", "Vui lòng chọn trạng thái ví", Map.of("active", "Trạng thái ví là bắt buộc"));
        }
        walletService.toggleStatus(workspaceId, walletId, requestedActive, userId);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật trạng thái ví thành công", null));
    }
}
