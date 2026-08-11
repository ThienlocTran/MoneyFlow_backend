package com.moneyflowbackend.insight.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SpendableBreakdownItem(
        SpendableSourceType sourceType,
        UUID sourceId,
        String sourceName,
        BigDecimal amount,
        LocalDate dueDate,
        InsightConfidence confidence,
        boolean includedInFormula
) {
}
