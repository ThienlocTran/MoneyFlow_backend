package com.moneyflowbackend.voice.asr;

public interface VoiceAsrClient {
    VoiceAsrProviderType provider();

    VoiceAsrTranscribeResult transcribe(VoiceAsrRequest request);
}
