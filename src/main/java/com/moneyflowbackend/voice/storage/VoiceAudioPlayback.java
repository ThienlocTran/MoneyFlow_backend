package com.moneyflowbackend.voice.storage;

import java.time.Instant;

public record VoiceAudioPlayback(String playbackUrl, Instant expiresAt, String mimeType) {
}
