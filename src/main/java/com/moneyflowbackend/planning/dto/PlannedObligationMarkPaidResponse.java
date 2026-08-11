package com.moneyflowbackend.planning.dto;

import com.moneyflowbackend.transaction.dto.TransactionResponse;

import java.util.UUID;

public record PlannedObligationMarkPaidResponse(PlannedObligationResponse obligation, UUID transactionId,
                                                TransactionResponse transaction) {
}
