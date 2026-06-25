package com.moneyflowbackend.debt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DebtResponse {
    private UUID id;
    private String counterpartyName;
    private String direction;
    private BigDecimal principalAmount;
    private BigDecimal paidAmount;
    private BigDecimal remainingAmount;
    private LocalDate openedOn;
    private LocalDate dueOn;
    private LocalDate closedOn;
    private String status;
    private String note;
    private long paymentCount;
}
