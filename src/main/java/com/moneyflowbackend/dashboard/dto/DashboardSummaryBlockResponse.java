package com.moneyflowbackend.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardSummaryBlockResponse {
    private BigDecimal income;
    private BigDecimal expense;
    private BigDecimal net;
    private long transactionCount;
}
