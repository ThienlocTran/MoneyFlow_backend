package com.moneyflowbackend.planning.dto;

import java.math.BigDecimal;

public record PlanningObligationSummaryResponse(BigDecimal upcomingAmount, BigDecimal overdueAmount,
                                                long upcomingCount, long overdueCount, long dueSoonCount,
                                                long paidCount, long cancelledCount, String currency) {
}
