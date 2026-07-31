package com.moneyflowbackend.voice.dto;

import lombok.Data;

@Data
public class VoiceReviewConfirmRequest {
    private String draftId;
    private VoiceReviewDraftRequest candidate;
}
