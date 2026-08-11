package com.moneyflowbackend.voice.session;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class VoiceSessionDraftResponse {
    private UUID draftId;
    private int draftIndex;
    private String sourceText;
    private String normalizedSourceText;
    private String type;
    private BigDecimal amount;
    private String currency;
    private UUID walletId;
    private UUID categoryId;
    private UUID jarId;
    private UUID fundId;
    private UUID debtId;
    private UUID counterpartyId;
    private String transactionType;
    private String movementType;
    private Boolean affectsWalletBalance;
    private boolean walletRequired;
    private boolean categoryRequired;
    private boolean confirmable;
    private VoiceSessionDraftStatus status;
    private List<VoiceSessionWarningResponse> warnings;
    private String confirmedEntityType;
    private UUID confirmedEntityId;
    private Instant confirmedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
