package com.moneyflowbackend.planning.dto;

import java.math.BigDecimal;

public record PlanningReserveSummaryResponse(BigDecimal activeAmount, long activeCount,
                                             BigDecimal releasedAmount, long releasedCount,
                                             BigDecimal cancelledAmount, long cancelledCount,
                                             String currency) {
}
