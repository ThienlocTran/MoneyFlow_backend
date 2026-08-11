package com.moneyflowbackend.planning.dto;

import java.time.Instant;
import java.util.UUID;

public record LinkPlannedObligationTransactionRequest(UUID transactionId, Instant paidAt, String note) {
}
