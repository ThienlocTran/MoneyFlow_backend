package com.moneyflowbackend.voice.storage;

public record StoredVoiceAudioStream(byte[] bytes, String mimeType, long sizeBytes) {
}
