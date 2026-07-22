package com.moneyflowbackend.savingsgoal.dto;

import com.moneyflowbackend.savingsgoal.model.SavingsGoalStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class SavingsGoalSummaryItemResponse {
    private UUID savingsGoalId;
    private String name;
    private SavingsGoalStatus status;
    private BigDecimal targetAmount;
    private BigDecimal reservedAmount;
    private BigDecimal remainingAmount;
    private BigDecimal progressPercent;
    private LocalDate targetDate;
}
