package com.moneyflowbackend.profile.avatar;

import com.moneyflowbackend.auth.dto.UserResponse;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/me")
public class ProfileAvatarController {
    private final ProfileAvatarService profileAvatarService;

    public ProfileAvatarController(ProfileAvatarService profileAvatarService) {
        this.profileAvatarService = profileAvatarService;
    }

    @PostMapping("/avatar")
    public ResponseEntity<ApiResponse<UserResponse>> uploadAvatar(@RequestPart(value = "file", required = false) MultipartFile file) {
        UserResponse res = profileAvatarService.upload(currentUserId(), file);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật ảnh đại diện thành công", res));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BusinessException("UNAUTHORIZED", "Chưa xác thực", HttpStatus.UNAUTHORIZED);
        }
        return UUID.fromString(auth.getName());
    }
}
