package com.moneyflowbackend.closing.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class DailyClosingVoicePreviewRequest {
    private String transcript;
    private LocalDate closingDate;
}
