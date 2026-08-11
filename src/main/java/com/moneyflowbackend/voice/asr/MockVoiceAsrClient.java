package com.moneyflowbackend.voice.asr;

import com.moneyflowbackend.voice.session.VoiceSessionAsrStatus;

import java.util.List;

public class MockVoiceAsrClient implements VoiceAsrClient {
    public static final String DEFAULT_TRANSCRIPT = "Hôm nay tôi ăn sáng hết 35 nghìn";

    @Override
    public VoiceAsrProviderType provider() {
        return VoiceAsrProviderType.MOCK;
    }

    @Override
    public VoiceAsrTranscribeResult transcribe(VoiceAsrRequest request) {
        return new VoiceAsrTranscribeResult(
                provider(),
                VoiceSessionAsrStatus.SUCCEEDED,
                "mock",
                request.language(),
                1000L,
                DEFAULT_TRANSCRIPT,
                DEFAULT_TRANSCRIPT,
                null,
                List.of(new VoiceAsrWarning("ASR_MOCK_TRANSCRIPT", VoiceAsrMessages.message("ASR_MOCK_TRANSCRIPT"))));
    }
}
