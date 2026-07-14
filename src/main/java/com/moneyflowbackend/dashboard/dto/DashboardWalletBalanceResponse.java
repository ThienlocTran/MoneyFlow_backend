package com.moneyflowbackend.dashboard.dto;

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
public class DashboardWalletBalanceResponse {
    private UUID walletId;
    private String walletName;
    private String walletType;
    private BigDecimal currentBalance;
    private boolean active;
    private boolean includeInTotal;
    private boolean defaultWallet;
}
