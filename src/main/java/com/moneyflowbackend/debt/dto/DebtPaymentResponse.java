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
public class DebtPaymentResponse {
    private UUID id;
    private UUID debtId;
    private BigDecimal amount;
    private LocalDate paymentDate;
    private String note;
}
