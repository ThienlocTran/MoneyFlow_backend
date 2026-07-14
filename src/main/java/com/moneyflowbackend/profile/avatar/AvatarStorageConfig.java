package com.moneyflowbackend.profile.avatar;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.net.http.HttpClient;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
public class AvatarStorageConfig {
    @Bean
    AvatarStorageService avatarStorageService(
            Clock clock,
            Environment environment,
            @Value("${MONEYFLOW_AVATAR_STORAGE_PROVIDER:${MONEYFLOW_AUDIO_STORAGE_PROVIDER:disabled}}") String provider,
            @Value("${MONEYFLOW_CLOUDINARY_CLOUD_NAME:}") String cloudName,
            @Value("${MONEYFLOW_CLOUDINARY_API_KEY:}") String apiKey,
            @Value("${MONEYFLOW_CLOUDINARY_API_SECRET:}") String apiSecret,
            @Value("${moneyflow.cloudinary.base-folder:${MONEYFLOW_CLOUDINARY_BASE_FOLDER:}}") String baseFolder) {
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
        return new CloudinaryAvatarStorageService(
                HttpClient.newHttpClient(),
                clock,
                cloudName,
                apiKey,
                apiSecret,
                resolveBaseFolder(baseFolder, environment));
    }

    static String resolveBaseFolder(String configured, Environment environment) {
        if (!isBlank(configured)) {
            return configured;
        }
        boolean production = Arrays.stream(environment.getActiveProfiles())
                .anyMatch("production"::equalsIgnoreCase);
        boolean test = Arrays.stream(environment.getActiveProfiles())
                .anyMatch("test"::equalsIgnoreCase);
        if (production) return "moneyflow/prod";
        if (test) return "moneyflow/test";
        return "moneyflow/dev";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
