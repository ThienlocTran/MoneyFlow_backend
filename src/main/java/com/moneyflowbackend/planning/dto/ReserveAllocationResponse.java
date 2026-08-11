package com.moneyflowbackend.planning.dto;

import com.moneyflowbackend.planning.model.ReserveAllocationStatus;
import com.moneyflowbackend.planning.model.ReservePurposeType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ReserveAllocationResponse(UUID id, UUID workspaceId, String name, BigDecimal amount, String currency,
                                        ReserveAllocationStatus status, ReservePurposeType purposeType,
                                        UUID walletId, String walletName, UUID categoryId, String categoryName,
                                        UUID jarId, String jarName, LocalDate targetDate, String note,
                                        List<String> warnings, Instant releasedAt, Instant cancelledAt,
                                        Instant createdAt, Instant updatedAt) {
}
