package com.moneyflowbackend.planning.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PlanningBreakdownItemResponse(
        String sourceType,
        UUID sourceId,
        String name,
        BigDecimal amount,
        boolean includedInPlanning,
        String reason,
        LocalDate dueDate,
        String status) {
}
