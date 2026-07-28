package com.moneyflowbackend.planning.dto;

import java.math.BigDecimal;
import java.util.List;

public record PlanningBreakdownGroupResponse(
        BigDecimal total,
        List<PlanningBreakdownItemResponse> items) {
}
