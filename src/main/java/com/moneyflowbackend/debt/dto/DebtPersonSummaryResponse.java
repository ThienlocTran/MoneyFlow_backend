package com.moneyflowbackend.debt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DebtPersonSummaryResponse {
    private String personName;
    private BigDecimal totalOriginalAmount;
    private BigDecimal totalPaid;
    private BigDecimal totalRemaining;
    private long openDebtCount;
    private long paidDebtCount;
    private LocalDate latestOpenedDate;
    private LocalDate latestPaymentDate;
    private List<DebtResponse> debts;
}
