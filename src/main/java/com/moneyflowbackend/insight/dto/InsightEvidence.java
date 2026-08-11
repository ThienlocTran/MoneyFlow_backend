package com.moneyflowbackend.insight.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InsightEvidence(
        String metricName,
        BigDecimal value,
        BigDecimal baselineValue,
        BigDecimal delta,
        BigDecimal ratio,
        BigDecimal percentage,
        UUID categoryId,
        String categoryName,
        UUID jarId,
        String jarName,
        LocalDate periodFrom,
        LocalDate periodTo,
        String ruleKey,
        long count
) {
}
