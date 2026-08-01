package com.moneyflowbackend.voice.command;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class VoiceCommandInterpretRequest {
    @Size(max = 500)
    private String text;
    private String timezone;
    private OffsetDateTime now;
    private String source;
}
