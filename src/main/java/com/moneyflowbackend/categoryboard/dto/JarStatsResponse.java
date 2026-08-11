package com.moneyflowbackend.categoryboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record JarStatsResponse(
        BigDecimal totalExpense,
        BigDecimal totalIncome,
        long transactionCount,
        long expenseTransactionCount,
        long incomeTransactionCount,
        BigDecimal percentageOfTotalExpense,
        LocalDate lastUsedAt,
        long activeCategoryCount,
        long usedCategoryCount,
        String currency) {
}
