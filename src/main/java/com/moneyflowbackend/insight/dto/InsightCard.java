package com.moneyflowbackend.insight.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InsightCard(
        String deterministicKey,
        InsightType type,
        InsightSeverity severity,
        String title,
        String message,
        BigDecimal amount,
        String currency,
        LocalDate periodFrom,
        LocalDate periodTo,
        List<InsightEvidence> evidence,
        String actionType,
        String actionLabel,
        String targetEntityType,
        UUID targetEntityId,
        InsightConfidence confidence,
        Instant generatedAt
) {
}
