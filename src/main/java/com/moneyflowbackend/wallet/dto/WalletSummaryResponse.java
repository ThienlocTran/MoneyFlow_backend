package com.moneyflowbackend.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletSummaryResponse {
    private BigDecimal totalBalance;
    private long activeWalletCount;
    private UUID defaultWalletId;
}
