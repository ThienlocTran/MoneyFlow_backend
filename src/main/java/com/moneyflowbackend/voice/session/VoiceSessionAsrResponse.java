package com.moneyflowbackend.voice.session;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class VoiceSessionAsrResponse {
    private String provider;
    private String model;
    private String language;
    private Long durationMs;
    private BigDecimal confidence;
    private List<VoiceSessionWarningResponse> warnings;
}
