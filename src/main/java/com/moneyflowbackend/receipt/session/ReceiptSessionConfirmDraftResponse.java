package com.moneyflowbackend.receipt.session;

import com.moneyflowbackend.transaction.dto.TransactionResponse;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ReceiptSessionConfirmDraftResponse {
    private UUID receiptSessionId;
    private UUID confirmedDraftId;
    private ReceiptSessionDraftStatus draftStatus;
    private String confirmedEntityType;
    private UUID confirmedEntityId;
    private boolean idempotentReplay;
    private List<ReceiptSessionWarningResponse> warnings;
    private SessionSummary session;
    private TransactionResponse transaction;

    @Data
    @Builder
    public static class SessionSummary {
        private ReceiptSessionStatus status;
    }
}
