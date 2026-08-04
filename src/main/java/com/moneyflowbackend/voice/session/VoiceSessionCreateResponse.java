package com.moneyflowbackend.voice.session;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class VoiceSessionCreateResponse {
    private UUID sessionId;
    private VoiceSessionSourceType sourceType;
    private VoiceSessionStatus status;
    private VoiceSessionAudioStatus audioStatus;
    private VoiceSessionAsrStatus asrStatus;
    private VoiceSessionCommandStatus commandStatus;
    private VoiceSessionConfirmStatus confirmStatus;
}
