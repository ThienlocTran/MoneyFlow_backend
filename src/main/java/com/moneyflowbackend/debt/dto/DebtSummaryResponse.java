package com.moneyflowbackend.debt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DebtSummaryResponse {
    private BigDecimal totalReceivableRemaining;
    private BigDecimal totalPayableRemaining;
    private long totalOpenDebts;
    private long totalPayments;
}
