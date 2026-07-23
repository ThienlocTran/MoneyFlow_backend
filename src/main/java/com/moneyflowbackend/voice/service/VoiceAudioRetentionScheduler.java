package com.moneyflowbackend.voice.service;

import com.moneyflowbackend.common.security.LogRedactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class VoiceAudioRetentionScheduler {
    private static final Logger log = LoggerFactory.getLogger(VoiceAudioRetentionScheduler.class);

    private final VoiceAudioService voiceAudioService;

    public VoiceAudioRetentionScheduler(VoiceAudioService voiceAudioService) {
        this.voiceAudioService = voiceAudioService;
    }

    @Scheduled(cron = "${VOICE_AUDIO_CLEANUP_CRON:0 30 3 * * *}")
    public void deleteExpiredVoiceAudio() {
        try {
            int deleted = voiceAudioService.deleteExpiredVoiceAudio();
            log.info("Voice audio retention cleanup completed: deleted={}", deleted);
        } catch (RuntimeException ex) {
            log.warn("Voice audio retention cleanup failed: {}", LogRedactor.redact(ex.getMessage()));
        }
    }
}
