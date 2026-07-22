package com.moneyflowbackend.savingsgoal.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SavingsGoalSummaryListResponse {
    private List<SavingsGoalSummaryItemResponse> items;
}
