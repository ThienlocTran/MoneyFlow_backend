package com.moneyflowbackend.voice.storage;

import com.moneyflowbackend.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

public class DisabledVoiceAudioStorageService implements VoiceAudioStorageService {
    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public String provider() {
        return "disabled";
    }

    @Override
    public StoredVoiceAudio upload(String objectKey, MultipartFile file) {
        throw notConfigured();
    }

    @Override
    public StoredVoiceAudioStream open(String storageKey, String mimeType) {
        throw notConfigured();
    }

    @Override
    public VoiceAudioPlayback playbackUrl(String storagePublicId, String mimeType) {
        throw notConfigured();
    }

    @Override
    public void delete(String storagePublicId) {
        throw notConfigured();
    }

    private BusinessException notConfigured() {
        return new BusinessException(
                "STORAGE_NOT_CONFIGURED",
                "Voice audio storage is not configured",
                HttpStatus.NOT_IMPLEMENTED);
    }
}
