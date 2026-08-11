package com.moneyflowbackend.planning.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PlannedObligationUpdateRequest(
        String name,
        BigDecimal amount,
        String currency,
        LocalDate dueDate,
        String priority,
        UUID walletId,
        UUID categoryId,
        String note,
        String recurrenceType
) {
}
