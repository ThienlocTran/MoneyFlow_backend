package com.moneyflowbackend.planning.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ReserveAllocationRequest(String name, BigDecimal amount, String currency, String purposeType,
                                       UUID walletId, UUID categoryId, UUID jarId, LocalDate targetDate, String note) {
}
