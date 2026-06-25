package com.moneyflowbackend.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardRecentTransactionResponse {
    private UUID id;
    private String type;
    private String status;
    private BigDecimal amount;
    private LocalDate transactionDate;
    private LocalTime transactionTime;
    private String description;
    private UUID walletId;
    private String walletName;
    private String sourceWalletName;
    private String destinationWalletName;
    private UUID categoryId;
    private String categoryName;
}
