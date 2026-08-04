package com.moneyflowbackend.voice.asr;

import com.moneyflowbackend.voice.session.VoiceSessionAsrStatus;

import java.util.List;

public class NoopVoiceAsrClient implements VoiceAsrClient {
    @Override
    public VoiceAsrProviderType provider() {
        return VoiceAsrProviderType.NONE;
    }

    @Override
    public VoiceAsrTranscribeResult transcribe(VoiceAsrRequest request) {
        return new VoiceAsrTranscribeResult(
                provider(),
                VoiceSessionAsrStatus.NOT_REQUESTED,
                null,
                request.language(),
                null,
                null,
                null,
                null,
                List.of(new VoiceAsrWarning("ASR_NOT_CONFIGURED", VoiceAsrMessages.message("ASR_NOT_CONFIGURED"))));
    }
}
