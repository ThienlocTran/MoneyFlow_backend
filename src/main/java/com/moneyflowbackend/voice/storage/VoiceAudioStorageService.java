package com.moneyflowbackend.voice.storage;

import org.springframework.web.multipart.MultipartFile;

public interface VoiceAudioStorageService {
    boolean isEnabled();
    String provider();
    StoredVoiceAudio upload(String objectKey, MultipartFile file);
    VoiceAudioPlayback playbackUrl(String storageKey, String mimeType);
    void delete(String storageKey);
}
