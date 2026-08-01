package com.moneyflowbackend.userpreferences.controller;

import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.dto.ApiResponse;
import com.moneyflowbackend.userpreferences.dto.UserPreferenceRequest;
import com.moneyflowbackend.userpreferences.dto.UserPreferenceResponse;
import com.moneyflowbackend.userpreferences.service.UserPreferenceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/me/preferences")
public class UserPreferenceController {
    private final UserPreferenceService preferenceService;

    public UserPreferenceController(UserPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<UserPreferenceResponse>> get() {
        return ResponseEntity.ok(ApiResponse.ok("User preferences loaded", preferenceService.get(currentUserId())));
    }

    @PatchMapping
    public ResponseEntity<ApiResponse<UserPreferenceResponse>> patch(@RequestBody(required = false) UserPreferenceRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("User preferences updated", preferenceService.patch(currentUserId(), request)));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BusinessException("UNAUTHORIZED", "Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        return UUID.fromString(auth.getName());
    }
}
