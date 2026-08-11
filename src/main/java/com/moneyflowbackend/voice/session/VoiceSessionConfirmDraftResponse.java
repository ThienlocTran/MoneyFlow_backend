package com.moneyflowbackend.voice.session;

import com.moneyflowbackend.transaction.dto.TransactionResponse;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class VoiceSessionConfirmDraftResponse {
    private UUID sessionId;
    private UUID draftId;
    private VoiceSessionDraftStatus draftStatus;
    private String confirmedEntityType;
    private UUID confirmedEntityId;
    private boolean idempotentReplay;
    private List<VoiceSessionWarningResponse> warnings;
    private SessionSummary session;
    private TransactionResponse transaction;

    @Data
    @Builder
    public static class SessionSummary {
        private VoiceSessionStatus status;
        private VoiceSessionConfirmStatus confirmStatus;
    }
}
