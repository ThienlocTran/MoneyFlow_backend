package com.moneyflowbackend.insight.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record FinancialPeriodMetric(
        UUID workspaceId,
        LocalDate from,
        LocalDate to,
        String currency,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal netCashflow,
        long transactionCount,
        long incomeTransactionCount,
        long expenseTransactionCount
) {
}
