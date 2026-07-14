package com.moneyflowbackend.profile.avatar;

import org.springframework.web.multipart.MultipartFile;

public interface AvatarStorageService {
    boolean isEnabled();

    String upload(String objectKey, MultipartFile file);
}
