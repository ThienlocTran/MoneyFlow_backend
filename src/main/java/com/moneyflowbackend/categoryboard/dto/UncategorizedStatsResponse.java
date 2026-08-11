package com.moneyflowbackend.categoryboard.dto;

import java.math.BigDecimal;

public record UncategorizedStatsResponse(
        BigDecimal totalExpense,
        long transactionCount,
        BigDecimal percentageOfTotalExpense,
        long categoryCount,
        String currency) {
}
