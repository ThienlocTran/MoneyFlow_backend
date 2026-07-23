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
            @Value("${VOICE_AUDIO_STORAGE_PROVIDER:${MONEYFLOW_AUDIO_STORAGE_PROVIDER:disabled}}") String provider,
            @Value("${VOICE_AUDIO_STORAGE_ENABLED:false}") boolean enabled,
            @Value("${MONEYFLOW_CLOUDINARY_CLOUD_NAME:}") String cloudName,
            @Value("${MONEYFLOW_CLOUDINARY_API_KEY:}") String apiKey,
            @Value("${MONEYFLOW_CLOUDINARY_API_SECRET:}") String apiSecret,
            @Value("${MONEYFLOW_AUDIO_FOLDER:moneyflow/voice}") String folder,
            @Value("${VOICE_AUDIO_S3_BUCKET:moneyflow-voice-audio}") String s3Bucket,
            @Value("${VOICE_AUDIO_S3_REGION:auto}") String s3Region,
            @Value("${VOICE_AUDIO_S3_ENDPOINT:}") String s3Endpoint,
            @Value("${VOICE_AUDIO_S3_ACCESS_KEY:}") String s3AccessKey,
            @Value("${VOICE_AUDIO_S3_SECRET_KEY:}") String s3SecretKey,
            @Value("${VOICE_AUDIO_S3_PATH_STYLE_ACCESS:true}") boolean s3PathStyleAccess) {
        if (!enabled || "disabled".equalsIgnoreCase(provider)) {
            return new DisabledVoiceAudioStorageService();
        }
        if ("s3".equalsIgnoreCase(provider) || "r2".equalsIgnoreCase(provider)) {
            List<String> missing = new ArrayList<>();
            if (isBlank(s3Bucket)) missing.add("VOICE_AUDIO_S3_BUCKET");
            if (isBlank(s3Endpoint)) missing.add("VOICE_AUDIO_S3_ENDPOINT");
            if (isBlank(s3AccessKey)) missing.add("VOICE_AUDIO_S3_ACCESS_KEY");
            if (isBlank(s3SecretKey)) missing.add("VOICE_AUDIO_S3_SECRET_KEY");
            if (!missing.isEmpty()) {
                throw new IllegalStateException("S3 voice audio storage config missing: " + String.join(", ", missing));
            }
            return new S3VoiceAudioStorageService(
                    HttpClient.newHttpClient(),
                    clock,
                    s3Bucket,
                    s3Region,
                    s3Endpoint,
                    s3AccessKey,
                    s3SecretKey,
                    s3PathStyleAccess);
        }
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
