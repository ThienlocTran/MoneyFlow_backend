package com.moneyflowbackend.planning.dto;

import java.util.List;

public record PlanningFormulaResponse(
        String label,
        String expression,
        List<PlanningFormulaComponentResponse> components) {
}
