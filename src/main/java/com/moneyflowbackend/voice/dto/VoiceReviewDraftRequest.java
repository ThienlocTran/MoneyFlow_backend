package com.moneyflowbackend.voice.dto;

import com.moneyflowbackend.common.model.SpendingScope;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class VoiceReviewDraftRequest {
    private String draftId;
    private VoiceReviewDraftType type;
    private BigDecimal amount;
    private OffsetDateTime occurredAt;
    private UUID walletId;
    private UUID sourceWalletId;
    private UUID categoryId;
    private UUID incomeSourceId;
    private String debtDirection;
    private UUID counterpartyId;
    private String counterpartyName;
    private UUID debtId;
    private UUID targetFundId;
    private UUID destinationWalletId;
    private UUID jarId;
    private String note;
    private SpendingScope scope;
}
