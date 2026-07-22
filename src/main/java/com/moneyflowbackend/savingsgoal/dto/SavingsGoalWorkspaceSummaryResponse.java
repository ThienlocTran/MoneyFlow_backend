package com.moneyflowbackend.savingsgoal.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class SavingsGoalWorkspaceSummaryResponse {
    private long goalCount;
    private BigDecimal totalTargetAmount;
    private BigDecimal totalReservedAmount;
    private BigDecimal totalRemainingAmount;
    private BigDecimal progressPercent;
}
