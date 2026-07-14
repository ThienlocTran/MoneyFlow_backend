package com.moneyflowbackend.profile.avatar;

import com.moneyflowbackend.auth.dto.UserResponse;
import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.model.UserStatus;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
public class ProfileAvatarService {
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final UserRepository userRepository;
    private final AvatarStorageService storageService;
    private final long maxBytes;

    public ProfileAvatarService(
            UserRepository userRepository,
            AvatarStorageService storageService,
            @Value("${MONEYFLOW_AVATAR_MAX_BYTES:2097152}") long maxBytes) {
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.maxBytes = maxBytes;
    }

    @Transactional
    public UserResponse upload(UUID userId, MultipartFile file) {
        validate(file);
        if (!storageService.isEnabled()) {
            throw new BusinessException("STORAGE_NOT_CONFIGURED", "Kho lưu trữ ảnh đại diện chưa được cấu hình.", HttpStatus.SERVICE_UNAVAILABLE);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "Chưa xác thực", HttpStatus.UNAUTHORIZED));
        if (user.getDeletedAt() != null || user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("UNAUTHORIZED", "Chưa xác thực", HttpStatus.UNAUTHORIZED);
        }

        String avatarUrl = storageService.upload("users/" + user.getId() + "/avatar", file);
        user.setAvatarUrl(avatarUrl);
        user.setUpdatedAt(Instant.now());
        return map(userRepository.save(user));
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("AVATAR_FILE_REQUIRED", "Vui lòng chọn ảnh đại diện.", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > maxBytes) {
            throw new BusinessException("AVATAR_FILE_TOO_LARGE", "Ảnh đại diện không được vượt quá 2MB.", HttpStatus.BAD_REQUEST);
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new BusinessException("INVALID_AVATAR_FILE_TYPE", "Ảnh đại diện phải là JPEG, PNG hoặc WebP.", HttpStatus.BAD_REQUEST);
        }
    }

    private UserResponse map(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus().name())
                .build();
    }
}
