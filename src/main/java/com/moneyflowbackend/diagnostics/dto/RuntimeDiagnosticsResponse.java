package com.moneyflowbackend.diagnostics.dto;

import java.util.List;

public record RuntimeDiagnosticsResponse(
        Backend backend,
        Database database,
        Storage storage,
        Security security) {

    public record Backend(
            String status,
            List<String> activeProfiles,
            String environmentRoot) {
    }

    public record Database(boolean ready) {
    }

    public record Storage(
            VoiceAudio voiceAudio,
            Avatar avatar,
            ReceiptOcr receiptOcr) {
    }

    public record VoiceAudio(
            String provider,
            boolean enabled,
            boolean configured,
            boolean cloudNamePresent,
            boolean apiKeyPresent,
            boolean apiSecretPresent,
            String baseFolder,
            long maxBytes) {
    }

    public record Avatar(
            String provider,
            boolean enabled,
            boolean configured,
            boolean cloudNamePresent,
            boolean apiKeyPresent,
            boolean apiSecretPresent,
            String baseFolder,
            long maxBytes) {
    }

    public record ReceiptOcr(
            String provider,
            boolean enabled,
            boolean configured,
            int maxImages,
            long maxImageBytes,
            int timeoutSeconds,
            String language,
            boolean serviceUrlConfigured,
            boolean externalServiceConfigured) {
    }

    public record Security(boolean authenticated) {
    }
}
