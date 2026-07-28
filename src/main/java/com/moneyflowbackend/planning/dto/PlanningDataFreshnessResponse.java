package com.moneyflowbackend.planning.dto;

import java.time.LocalDate;

public record PlanningDataFreshnessResponse(
        LocalDate latestDailyClosingDate,
        String walletBalanceFreshness) {
}
