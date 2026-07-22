package com.moneyflowbackend.savingsgoal.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class SavingsGoalLedgerRequest {
    private BigDecimal amount;

    @Size(max = 500, message = "Savings goal ledger note must not exceed 500 characters")
    private String note;

    private Instant occurredAt;
}
