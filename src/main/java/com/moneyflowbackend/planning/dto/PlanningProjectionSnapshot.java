package com.moneyflowbackend.planning.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PlanningProjectionSnapshot(UUID workspaceId, LocalDate asOfDate, int horizonDays, String currency,
                                         BigDecimal availableLedgerBalance, BigDecimal activeReserveAmount,
                                         BigDecimal upcomingRequiredOutflowAmount,
                                         BigDecimal overdueRequiredOutflowAmount,
                                         BigDecimal expectedIncomingAmount, BigDecimal actuallySpendable,
                                         BigDecimal projectedShortfall,
                                         List<PlanningProjectionBreakdownItem> reserveBreakdown,
                                         List<PlanningProjectionBreakdownItem> obligationBreakdown,
                                         List<PlanningProjectionBreakdownItem> expectedIncomingBreakdown,
                                         List<PlanningProjectionWarning> warnings, Instant generatedAt) {
}
