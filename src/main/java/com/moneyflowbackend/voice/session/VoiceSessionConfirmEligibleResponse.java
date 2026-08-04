package com.moneyflowbackend.voice.session;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class VoiceSessionConfirmEligibleResponse {
    private UUID sessionId;
    private int confirmedCount;
    private int skippedCount;
    private List<VoiceSessionConfirmDraftResponse> results;
    private VoiceSessionStatus sessionStatus;
    private VoiceSessionConfirmStatus confirmStatus;
    private List<VoiceSessionWarningResponse> warnings;
}
