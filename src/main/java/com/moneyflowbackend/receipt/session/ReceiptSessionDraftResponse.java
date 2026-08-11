package com.moneyflowbackend.receipt.session;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ReceiptSessionDraftResponse {
    private UUID draftId;
    private int draftIndex;
    private String type;
    private ReceiptSessionDraftStatus status;
    private BigDecimal amount;
    private String currency;
    private LocalDate transactionDate;
    private UUID walletId;
    private UUID categoryId;
    private String categoryHint;
    private String merchantName;
    private String note;
    private String sourceText;
    private Double confidence;
    private String confirmedEntityType;
    private UUID confirmedEntityId;
    private Instant confirmedAt;
    private List<ReceiptSessionWarningResponse> warnings;
    private Instant createdAt;
    private Instant updatedAt;
}
