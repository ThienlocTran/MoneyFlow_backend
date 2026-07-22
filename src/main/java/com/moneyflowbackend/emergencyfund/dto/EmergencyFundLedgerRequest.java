package com.moneyflowbackend.emergencyfund.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class EmergencyFundLedgerRequest {
    private BigDecimal amount;
    private String note;
    private Instant occurredAt;
}
