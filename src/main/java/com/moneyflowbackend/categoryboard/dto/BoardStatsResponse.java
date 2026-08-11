package com.moneyflowbackend.categoryboard.dto;

import java.math.BigDecimal;

public record BoardStatsResponse(
        BigDecimal totalExpense,
        BigDecimal totalIncome,
        long totalTransactionCount,
        BigDecimal categorizedExpense,
        BigDecimal uncategorizedExpense,
        long uncategorizedCount,
        String currency) {
}
