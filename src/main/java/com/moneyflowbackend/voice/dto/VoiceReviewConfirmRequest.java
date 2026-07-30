package com.moneyflowbackend.voice.dto;

import lombok.Data;

@Data
public class VoiceReviewConfirmRequest {
    private VoiceReviewDraftRequest candidate;
}
