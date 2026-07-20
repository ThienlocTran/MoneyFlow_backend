package com.moneyflowbackend.closing.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class DailyClosingWalletResponse {
    private UUID walletId;
    private String walletName;
    private String walletType;
    private BigDecimal ledgerBalance;
    private BigDecimal actualBalance;
    private BigDecimal difference;
    private UUID snapshotId;
    private String reconciliationStatus;
    private String sourceType;
    private Instant recordedAt;
    private String note;
}
