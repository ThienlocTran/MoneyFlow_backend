package com.moneyflowbackend.voice.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class VoiceAudioStorageConfig {
    @Bean
    VoiceAudioStorageService voiceAudioStorageService(
            Clock clock,
            @Value("${MONEYFLOW_AUDIO_STORAGE_PROVIDER:disabled}") String provider,
            @Value("${MONEYFLOW_CLOUDINARY_CLOUD_NAME:}") String cloudName,
            @Value("${MONEYFLOW_CLOUDINARY_API_KEY:}") String apiKey,
            @Value("${MONEYFLOW_CLOUDINARY_API_SECRET:}") String apiSecret,
            @Value("${MONEYFLOW_AUDIO_FOLDER:moneyflow/voice}") String folder) {
        if (!"cloudinary".equalsIgnoreCase(provider)) {
            return new DisabledVoiceAudioStorageService();
        }
        List<String> missing = new ArrayList<>();
        if (isBlank(cloudName)) missing.add("MONEYFLOW_CLOUDINARY_CLOUD_NAME");
        if (isBlank(apiKey)) missing.add("MONEYFLOW_CLOUDINARY_API_KEY");
        if (isBlank(apiSecret)) missing.add("MONEYFLOW_CLOUDINARY_API_SECRET");
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Cloudinary voice audio storage config missing: " + String.join(", ", missing));
        }
        return new CloudinaryVoiceAudioStorageService(
                HttpClient.newHttpClient(),
                clock,
                cloudName,
                apiKey,
                apiSecret,
                folder);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
