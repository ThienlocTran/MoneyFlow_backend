package com.moneyflowbackend.planning.dto;

import java.math.BigDecimal;

public record PlanningBreakdownResponse(
        PlanningBreakdownGroupResponse walletAvailable,
        PlanningBreakdownGroupResponse reserves,
        BigDecimal emergencyFund,
        BigDecimal savingsGoals,
        BigDecimal sinkingFunds,
        PlanningBreakdownGroupResponse obligations,
        PlanningBreakdownGroupResponse payableDebts,
        PlanningSimulationBreakdownResponse simulations) {
}
