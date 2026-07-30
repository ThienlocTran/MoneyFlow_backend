package com.moneyflowbackend.voice.dto;

import lombok.Data;

@Data
public class VoiceReviewParseRequest {
    private String text;
    private String transcript;
    private String rawInput;
    private String idempotencyKey;
    private Integer durationSeconds;
    private String audioMimeType;
}
