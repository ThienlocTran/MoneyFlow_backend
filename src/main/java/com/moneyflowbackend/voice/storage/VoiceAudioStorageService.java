package com.moneyflowbackend.voice.storage;

import org.springframework.web.multipart.MultipartFile;

public interface VoiceAudioStorageService {
    boolean isEnabled();
    StoredVoiceAudio upload(String objectKey, MultipartFile file);
    VoiceAudioPlayback playbackUrl(String storagePublicId, String mimeType);
    void delete(String storagePublicId);
}
