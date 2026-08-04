package com.moneyflowbackend.voice.asr;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class VoiceAsrProperties {
    private final VoiceAsrProviderType provider;
    private final String serviceUrl;
    private final int timeoutSeconds;
    private final double maxAudioSeconds;
    private final double minAudioSeconds;
    private final long maxFileBytes;
    private final String language;
    private final boolean returnSegments;
    private final Set<String> allowedMimeTypes;

    public VoiceAsrProperties(
            @Value("${MONEYFLOW_ASR_PROVIDER:none}") String provider,
            @Value("${MONEYFLOW_ASR_SERVICE_URL:http://localhost:8092}") String serviceUrl,
            @Value("${MONEYFLOW_ASR_TIMEOUT_SECONDS:60}") int timeoutSeconds,
            @Value("${MONEYFLOW_ASR_MAX_AUDIO_SECONDS:60}") double maxAudioSeconds,
            @Value("${MONEYFLOW_ASR_MIN_AUDIO_SECONDS:0.8}") double minAudioSeconds,
            @Value("${MONEYFLOW_ASR_MAX_FILE_BYTES:26214400}") long maxFileBytes,
            @Value("${MONEYFLOW_ASR_LANGUAGE:vi}") String language,
            @Value("${MONEYFLOW_ASR_RETURN_SEGMENTS:false}") boolean returnSegments,
            @Value("${MONEYFLOW_ASR_ALLOWED_MIME_TYPES:audio/webm,audio/ogg,audio/wav,audio/mpeg,audio/mp4,audio/x-m4a}") String allowedMimeTypes) {
        this.provider = parseProvider(provider);
        this.serviceUrl = serviceUrl == null ? "" : serviceUrl.trim();
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
        this.maxAudioSeconds = Math.max(0.1, maxAudioSeconds);
        this.minAudioSeconds = Math.max(0, minAudioSeconds);
        this.maxFileBytes = Math.max(1, maxFileBytes);
        this.language = language == null || language.isBlank() ? "vi" : language.trim();
        this.returnSegments = returnSegments;
        this.allowedMimeTypes = parseMimeTypes(allowedMimeTypes);
    }

    public VoiceAsrProviderType provider() {
        return provider;
    }

    public String serviceUrl() {
        return serviceUrl;
    }

    public boolean externalServiceConfigured() {
        return !serviceUrl.isBlank();
    }

    public int timeoutSeconds() {
        return timeoutSeconds;
    }

    public double maxAudioSeconds() {
        return maxAudioSeconds;
    }

    public double minAudioSeconds() {
        return minAudioSeconds;
    }

    public long maxFileBytes() {
        return maxFileBytes;
    }

    public String language() {
        return language;
    }

    public boolean returnSegments() {
        return returnSegments;
    }

    public Set<String> allowedMimeTypes() {
        return allowedMimeTypes;
    }

    private VoiceAsrProviderType parseProvider(String raw) {
        if (raw == null || raw.isBlank()) return VoiceAsrProviderType.NONE;
        try {
            return VoiceAsrProviderType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return VoiceAsrProviderType.NONE;
        }
    }

    private Set<String> parseMimeTypes(String raw) {
        return Arrays.stream((raw == null ? "" : raw).split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }
}
