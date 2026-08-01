package com.moneyflowbackend.closing.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class WalletSnapshotRequest {
    private BigDecimal actualBalance;

    @NotNull
    private Instant recordedAt;

    private String balanceMode;
    private String sourceType;
    private String note;
}
