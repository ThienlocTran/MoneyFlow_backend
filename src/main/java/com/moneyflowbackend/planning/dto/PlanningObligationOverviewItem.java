package com.moneyflowbackend.planning.dto;

import com.moneyflowbackend.planning.model.PlannedObligationComputedState;
import com.moneyflowbackend.planning.model.PlannedObligationPriority;
import com.moneyflowbackend.planning.model.PlannedObligationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PlanningObligationOverviewItem(UUID id, String name, BigDecimal amount, String currency,
                                             LocalDate dueDate, long daysUntilDue, long daysOverdue,
                                             PlannedObligationPriority priority, PlannedObligationStatus status,
                                             PlannedObligationComputedState computedState,
                                             UUID categoryId, String categoryName, UUID walletId, String walletName) {
}
