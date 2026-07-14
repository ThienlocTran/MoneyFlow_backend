package com.moneyflowbackend.profile.avatar;

import org.springframework.web.multipart.MultipartFile;

public class DisabledAvatarStorageService implements AvatarStorageService {
    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public String upload(String objectKey, MultipartFile file) {
        throw new IllegalStateException("Avatar storage is disabled");
    }
}
