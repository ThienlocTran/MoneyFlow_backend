package com.moneyflowbackend.categoryboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CategoryStatsResponse(
        BigDecimal totalExpense,
        BigDecimal totalIncome,
        long transactionCount,
        long expenseTransactionCount,
        long incomeTransactionCount,
        BigDecimal percentageOfTotalExpense,
        LocalDate lastUsedAt,
        String currency) {
}
