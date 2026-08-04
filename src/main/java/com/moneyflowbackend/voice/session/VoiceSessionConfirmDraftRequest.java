package com.moneyflowbackend.voice.session;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class VoiceSessionConfirmDraftRequest {
    private BigDecimal amount;
    private UUID walletId;
    private UUID categoryId;
    private UUID jarId;
    private OffsetDateTime occurredAt;
    private String note;
    private String confirmClientRequestId;
}
