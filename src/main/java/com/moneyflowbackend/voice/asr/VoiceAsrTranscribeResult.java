package com.moneyflowbackend.voice.asr;

import com.moneyflowbackend.voice.session.VoiceSessionAsrStatus;

import java.math.BigDecimal;
import java.util.List;

public record VoiceAsrTranscribeResult(
        VoiceAsrProviderType provider,
        VoiceSessionAsrStatus status,
        String model,
        String language,
        Long durationMs,
        String transcript,
        String normalizedTranscript,
        BigDecimal confidence,
        List<VoiceAsrWarning> warnings) {

    public boolean succeeded() {
        return status == VoiceSessionAsrStatus.SUCCEEDED;
    }
}
