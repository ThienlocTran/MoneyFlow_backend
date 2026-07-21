package com.moneyflowbackend.obligation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancialInboxSummaryResponse {
    private long overdueCount;
    private long dueTodayCount;
    private long upcomingCount;
    private long snoozedCount;
    private long totalPendingCount;
}
