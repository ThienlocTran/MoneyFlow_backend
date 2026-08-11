package com.moneyflowbackend.insight.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ActuallySpendableSnapshot(
        UUID workspaceId,
        LocalDate asOfDate,
        int horizonDays,
        String currency,
        BigDecimal availableLedgerBalance,
        BigDecimal activeReserveAmount,
        BigDecimal upcomingRequiredOutflowAmount,
        BigDecimal overdueRequiredOutflowAmount,
        BigDecimal actuallySpendable,
        BigDecimal expectedIncomingAmount,
        List<SpendableBreakdownItem> sourceBreakdowns,
        List<SpendableDataQualityWarning> dataQualityWarnings,
        Instant generatedAt
) {
}
