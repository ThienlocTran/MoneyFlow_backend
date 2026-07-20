package com.moneyflowbackend.closing.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class WalletSnapshotRequest {
    @NotNull
    private BigDecimal actualBalance;

    @NotNull
    private Instant recordedAt;

    private String sourceType;
    private String note;
}
