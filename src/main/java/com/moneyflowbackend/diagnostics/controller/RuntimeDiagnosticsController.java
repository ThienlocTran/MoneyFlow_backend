package com.moneyflowbackend.diagnostics.controller;

import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.diagnostics.dto.RuntimeDiagnosticsResponse;
import com.moneyflowbackend.diagnostics.service.RuntimeDiagnosticsService;
import com.moneyflowbackend.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/me/runtime-diagnostics")
public class RuntimeDiagnosticsController {
    private final RuntimeDiagnosticsService diagnosticsService;

    public RuntimeDiagnosticsController(RuntimeDiagnosticsService diagnosticsService) {
        this.diagnosticsService = diagnosticsService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<RuntimeDiagnosticsResponse>> get() {
        UUID ignored = currentUserId();
        return ResponseEntity.ok(ApiResponse.ok("Runtime diagnostics loaded", diagnosticsService.diagnostics(true)));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BusinessException("UNAUTHORIZED", "Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        return UUID.fromString(auth.getName());
    }
}
