package com.moneyflowbackend.profile.avatar;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class AvatarStorageConfig {
    @Bean
    AvatarStorageService avatarStorageService(
            Clock clock,
            @Value("${MONEYFLOW_AVATAR_STORAGE_PROVIDER:${MONEYFLOW_AUDIO_STORAGE_PROVIDER:disabled}}") String provider,
            @Value("${MONEYFLOW_CLOUDINARY_CLOUD_NAME:}") String cloudName,
            @Value("${MONEYFLOW_CLOUDINARY_API_KEY:}") String apiKey,
            @Value("${MONEYFLOW_CLOUDINARY_API_SECRET:}") String apiSecret,
            @Value("${MONEYFLOW_AVATAR_FOLDER:moneyflow/avatars}") String folder) {
        if (!"cloudinary".equalsIgnoreCase(provider)) {
            return new DisabledAvatarStorageService();
        }
        List<String> missing = new ArrayList<>();
        if (isBlank(cloudName)) missing.add("MONEYFLOW_CLOUDINARY_CLOUD_NAME");
        if (isBlank(apiKey)) missing.add("MONEYFLOW_CLOUDINARY_API_KEY");
        if (isBlank(apiSecret)) missing.add("MONEYFLOW_CLOUDINARY_API_SECRET");
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Cloudinary avatar storage config missing: " + String.join(", ", missing));
        }
        return new CloudinaryAvatarStorageService(HttpClient.newHttpClient(), clock, cloudName, apiKey, apiSecret, folder);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
