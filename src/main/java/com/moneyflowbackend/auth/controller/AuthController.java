package com.moneyflowbackend.auth.controller;

import com.moneyflowbackend.auth.dto.*;
import com.moneyflowbackend.auth.service.AuthService;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/public/auth/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest req) {
        UserResponse res = authService.register(req);
        return ResponseEntity.ok(ApiResponse.ok("Đăng ký thành công", res));
    }

    @PostMapping("/public/auth/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest req) {
        TokenResponse res = authService.login(req);
        return ResponseEntity.ok(ApiResponse.ok("Đăng nhập thành công", res));
    }

    @PostMapping("/public/auth/google")
    public ResponseEntity<ApiResponse<TokenResponse>> googleLogin(@Valid @RequestBody GoogleLoginRequest req) {
        TokenResponse res = authService.googleLogin(req);
        return ResponseEntity.ok(ApiResponse.ok("Đăng nhập bằng Google thành công", res));
    }

    @PostMapping("/public/auth/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshRequest req) {
        TokenResponse res = authService.refresh(req);
        return ResponseEntity.ok(ApiResponse.ok("Làm mới token thành công", res));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody RefreshRequest req) {
        authService.logout(req.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.ok("Đăng xuất thành công", null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me() {
        UUID userId = currentUserId();
        UserResponse res = authService.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.ok("Lấy thông tin cá nhân thành công", res));
    }

    @GetMapping("/users/me")
    public ResponseEntity<ApiResponse<UserResponse>> usersMe() {
        return me();
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateMe(@Valid @RequestBody UserResponse req) {
        UUID userId = currentUserId();
        UserResponse res = authService.updateProfile(userId, req);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật thông tin cá nhân thành công", res));
    }

    @PutMapping("/users/me/username")
    public ResponseEntity<ApiResponse<UserResponse>> updateUsername(@Valid @RequestBody UsernameUpdateRequest req) {
        UUID userId = currentUserId();
        UserResponse res = authService.updateUsername(userId, req);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật tên đăng nhập thành công", res));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BusinessException("UNAUTHORIZED", "Chưa xác thực", HttpStatus.UNAUTHORIZED);
        }
        return UUID.fromString(auth.getName());
    }
}
