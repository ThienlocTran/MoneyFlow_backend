package com.moneyflowbackend.receipt.session.storage;

import com.moneyflowbackend.profile.avatar.AvatarStorageConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.net.http.HttpClient;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class ReceiptImageStorageConfig {
    @Bean
    ReceiptImageStorageService receiptImageStorageService(
            Clock clock,
            Environment environment,
            @Value("${MONEYFLOW_RECEIPT_IMAGE_STORAGE_PROVIDER:disabled}") String provider,
            @Value("${MONEYFLOW_CLOUDINARY_CLOUD_NAME:}") String cloudName,
            @Value("${MONEYFLOW_CLOUDINARY_API_KEY:}") String apiKey,
            @Value("${MONEYFLOW_CLOUDINARY_API_SECRET:}") String apiSecret,
            @Value("${moneyflow.cloudinary.base-folder:${MONEYFLOW_CLOUDINARY_BASE_FOLDER:}}") String baseFolder) {
        if (!"cloudinary".equalsIgnoreCase(provider)) {
            return new DisabledReceiptImageStorageService();
        }
        List<String> missing = new ArrayList<>();
        if (isBlank(cloudName)) missing.add("MONEYFLOW_CLOUDINARY_CLOUD_NAME");
        if (isBlank(apiKey)) missing.add("MONEYFLOW_CLOUDINARY_API_KEY");
        if (isBlank(apiSecret)) missing.add("MONEYFLOW_CLOUDINARY_API_SECRET");
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Cloudinary receipt image storage config missing: " + String.join(", ", missing));
        }
        return new CloudinaryReceiptImageStorageService(
                HttpClient.newHttpClient(),
                clock,
                cloudName,
                apiKey,
                apiSecret,
                AvatarStorageConfig.resolveBaseFolder(baseFolder, environment));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
