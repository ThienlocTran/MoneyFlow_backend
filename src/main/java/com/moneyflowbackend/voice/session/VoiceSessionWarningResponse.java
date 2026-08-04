package com.moneyflowbackend.voice.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceSessionWarningResponse {
    private String code;
    private String field;
    private String message;
}
