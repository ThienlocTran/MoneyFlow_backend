package com.moneyflowbackend.diagnostics.service;

import com.moneyflowbackend.diagnostics.dto.RuntimeDiagnosticsResponse;
import com.moneyflowbackend.profile.avatar.AvatarStorageService;
import com.moneyflowbackend.voice.storage.VoiceAudioStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;

@Service
public class RuntimeDiagnosticsService {
    private final DataSource dataSource;
    private final Environment environment;
    private final VoiceAudioStorageService voiceAudioStorageService;
    private final AvatarStorageService avatarStorageService;
    private final String voiceProvider;
    private final String avatarProvider;
    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final String cloudinaryBaseFolder;
    private final long voiceMaxBytes;
    private final long avatarMaxBytes;

    public RuntimeDiagnosticsService(
            DataSource dataSource,
            Environment environment,
            VoiceAudioStorageService voiceAudioStorageService,
            AvatarStorageService avatarStorageService,
            @Value("${VOICE_AUDIO_STORAGE_PROVIDER:${MONEYFLOW_AUDIO_STORAGE_PROVIDER:disabled}}") String voiceProvider,
            @Value("${MONEYFLOW_AVATAR_STORAGE_PROVIDER:${MONEYFLOW_AUDIO_STORAGE_PROVIDER:disabled}}") String avatarProvider,
            @Value("${MONEYFLOW_CLOUDINARY_CLOUD_NAME:}") String cloudName,
            @Value("${MONEYFLOW_CLOUDINARY_API_KEY:}") String apiKey,
            @Value("${MONEYFLOW_CLOUDINARY_API_SECRET:}") String apiSecret,
            @Value("${moneyflow.cloudinary.base-folder:${MONEYFLOW_CLOUDINARY_BASE_FOLDER:}}") String cloudinaryBaseFolder,
            @Value("${VOICE_AUDIO_MAX_BYTES:${MONEYFLOW_AUDIO_MAX_BYTES:10485760}}") long voiceMaxBytes,
            @Value("${MONEYFLOW_AVATAR_MAX_BYTES:2097152}") long avatarMaxBytes) {
        this.dataSource = dataSource;
        this.environment = environment;
        this.voiceAudioStorageService = voiceAudioStorageService;
        this.avatarStorageService = avatarStorageService;
        this.voiceProvider = cleanProvider(voiceProvider);
        this.avatarProvider = cleanProvider(avatarProvider);
        this.cloudName = cloudName;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.cloudinaryBaseFolder = cloudinaryBaseFolder;
        this.voiceMaxBytes = Math.max(1, voiceMaxBytes);
        this.avatarMaxBytes = Math.max(1, avatarMaxBytes);
    }

    public RuntimeDiagnosticsResponse diagnostics(boolean authenticated) {
        String root = environmentRoot();
        boolean cloudNamePresent = present(cloudName);
        boolean apiKeyPresent = present(apiKey);
        boolean apiSecretPresent = present(apiSecret);

        return new RuntimeDiagnosticsResponse(
                new RuntimeDiagnosticsResponse.Backend("UP", Arrays.asList(environment.getActiveProfiles()), root),
                new RuntimeDiagnosticsResponse.Database(databaseReady()),
                new RuntimeDiagnosticsResponse.Storage(
                        new RuntimeDiagnosticsResponse.VoiceAudio(
                                voiceAudioStorageService.provider(),
                                voiceAudioStorageService.isEnabled(),
                                configured(voiceProvider, cloudNamePresent, apiKeyPresent, apiSecretPresent),
                                cloudNamePresent,
                                apiKeyPresent,
                                apiSecretPresent,
                                baseFolder(root),
                                voiceMaxBytes),
                        new RuntimeDiagnosticsResponse.Avatar(
                                avatarProvider,
                                avatarStorageService.isEnabled(),
                                configured(avatarProvider, cloudNamePresent, apiKeyPresent, apiSecretPresent),
                                cloudNamePresent,
                                apiKeyPresent,
                                apiSecretPresent,
                                baseFolder(root),
                                avatarMaxBytes)),
                new RuntimeDiagnosticsResponse.Security(authenticated));
    }

    private boolean databaseReady() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT 1")) {
            return resultSet.next();
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean configured(String provider, boolean cloudNamePresent, boolean apiKeyPresent, boolean apiSecretPresent) {
        return "cloudinary".equals(provider) && cloudNamePresent && apiKeyPresent && apiSecretPresent;
    }

    private String environmentRoot() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "production".equalsIgnoreCase(profile) || "prod".equalsIgnoreCase(profile))
                ? "production"
                : "dev";
    }

    private String baseFolder(String fallback) {
        return present(cloudinaryBaseFolder) ? trimSlashes(cloudinaryBaseFolder) : fallback;
    }

    private String cleanProvider(String provider) {
        return present(provider) ? provider.trim().toLowerCase() : "disabled";
    }

    private String trimSlashes(String value) {
        return value.replaceAll("^/+", "").replaceAll("/+$", "");
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
