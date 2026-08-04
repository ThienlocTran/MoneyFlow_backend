package com.moneyflowbackend.voice.asr;

import java.util.UUID;

public record VoiceAsrRequest(
        UUID sessionId,
        byte[] audio,
        String filename,
        String contentType,
        String language,
        boolean returnSegments,
        boolean normalize) {
}
