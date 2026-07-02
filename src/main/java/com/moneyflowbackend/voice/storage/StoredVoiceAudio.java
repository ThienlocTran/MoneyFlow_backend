package com.moneyflowbackend.voice.storage;

public record StoredVoiceAudio(String provider, String storagePublicId, String audioUrl) {
}
