package com.moneyflowbackend.voice.session;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class VoiceSessionInterpretRequest {
    private String timezone;
    private OffsetDateTime now;
}
