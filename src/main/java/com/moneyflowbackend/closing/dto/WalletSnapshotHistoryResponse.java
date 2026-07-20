package com.moneyflowbackend.closing.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class WalletSnapshotHistoryResponse {
    private UUID snapshotId;
    private UUID dailyClosingId;
    private UUID walletId;
    private String walletName;
    private LocalDate snapshotDate;
    private Instant recordedAt;
    private BigDecimal actualBalance;
    private BigDecimal ledgerBalance;
    private BigDecimal difference;
    private String reconciliationStatus;
    private String sourceType;
    private String note;
    private Instant createdAt;
}
