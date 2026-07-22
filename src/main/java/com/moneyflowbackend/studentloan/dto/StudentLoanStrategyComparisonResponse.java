package com.moneyflowbackend.studentloan.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class StudentLoanStrategyComparisonResponse {
    private UUID workspaceId;
    private BigDecimal extraMonthlyBudget;
    private List<StudentLoanStrategyResultResponse> results;
}
