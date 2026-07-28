package com.moneyflowbackend.planning.dto;

public record PlanningSimulationBreakdownResponse(
        boolean studentLoansIncluded,
        String note) {
}
