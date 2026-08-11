package com.moneyflowbackend.planning.dto;

import com.moneyflowbackend.planning.model.ReservePurposeType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PlanningReserveOverviewItem(UUID id, String name, BigDecimal amount, String currency,
                                          ReservePurposeType purposeType, LocalDate targetDate,
                                          UUID walletId, String walletName, UUID categoryId, String categoryName,
                                          UUID jarId, String jarName) {
}
