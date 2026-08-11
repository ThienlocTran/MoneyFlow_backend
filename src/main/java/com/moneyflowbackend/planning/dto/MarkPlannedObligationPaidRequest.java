package com.moneyflowbackend.planning.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record MarkPlannedObligationPaidRequest(UUID walletId, UUID categoryId, BigDecimal amount,
                                               LocalDate transactionDate, String note) {
}
