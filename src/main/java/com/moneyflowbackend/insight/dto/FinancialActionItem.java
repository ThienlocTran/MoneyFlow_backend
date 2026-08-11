package com.moneyflowbackend.insight.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record FinancialActionItem(
        String deterministicKey,
        ActionItemType type,
        InsightSeverity severity,
        String title,
        String message,
        long count,
        BigDecimal amount,
        String currency,
        LocalDate periodFrom,
        LocalDate periodTo,
        String targetEntityType,
        UUID targetEntityId,
        String targetRoute,
        String actionLabel,
        List<InsightEvidence> evidence,
        Instant generatedAt
) {
}
