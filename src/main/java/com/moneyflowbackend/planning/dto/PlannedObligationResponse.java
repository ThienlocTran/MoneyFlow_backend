package com.moneyflowbackend.planning.dto;

import com.moneyflowbackend.planning.model.PlannedObligationComputedState;
import com.moneyflowbackend.planning.model.PlannedObligationPriority;
import com.moneyflowbackend.planning.model.PlannedObligationStatus;
import com.moneyflowbackend.planning.model.PlanningRecurrenceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PlannedObligationResponse(
        UUID id,
        UUID workspaceId,
        String name,
        BigDecimal amount,
        String currency,
        LocalDate dueDate,
        PlannedObligationStatus status,
        PlannedObligationComputedState computedState,
        PlannedObligationPriority priority,
        UUID walletId,
        String walletName,
        UUID categoryId,
        String categoryName,
        UUID jarId,
        String jarName,
        String note,
        PlanningRecurrenceType recurrenceType,
        UUID linkedTransactionId,
        Instant createdAt,
        Instant updatedAt
) {
}
