package com.moneyflowbackend.planning.dto;

import java.math.BigDecimal;

public record PlanningFormulaComponentResponse(
        String label,
        BigDecimal amount,
        String sign) {
}
