package com.moneyflowbackend.insight.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record FinancialActionItemReport(
        UUID workspaceId,
        LocalDate periodFrom,
        LocalDate periodTo,
        LocalDate asOfDate,
        List<FinancialActionItem> actionItems,
        List<SpendableDataQualityWarning> dataQualityWarnings,
        Instant generatedAt
) {
}
