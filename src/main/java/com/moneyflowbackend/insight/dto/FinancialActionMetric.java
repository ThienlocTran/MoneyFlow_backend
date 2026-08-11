package com.moneyflowbackend.insight.dto;

import java.math.BigDecimal;

public record FinancialActionMetric(
        BigDecimal amount,
        long count
) {
    public static FinancialActionMetric zero() {
        return new FinancialActionMetric(BigDecimal.ZERO, 0);
    }
}
