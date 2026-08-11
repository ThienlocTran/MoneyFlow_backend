package com.moneyflowbackend.planning.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PlanningOverviewResponse(UUID workspaceId, LocalDate asOfDate, int horizonDays, Instant generatedAt,
                                       String currency, PlanningProjectionSnapshot projection,
                                       PlanningObligationSummaryResponse obligationSummary,
                                       PlanningReserveSummaryResponse reserveSummary,
                                       List<PlanningObligationOverviewItem> upcomingObligations,
                                       List<PlanningObligationOverviewItem> overdueObligations,
                                       List<PlanningReserveOverviewItem> activeReserves,
                                       List<PlanningActionItemResponse> actionItems,
                                       List<PlanningProjectionWarning> warnings) {
}
