package com.moneyflowbackend.insight.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record MetricBreakdownRow(
        UUID id,
        String name,
        BigDecimal amount,
        long count,
        BigDecimal percentage
) {
}
