package com.moneyflowbackend.obligation.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class ConfirmOccurrenceRequest {
    private Boolean confirmed;
    private BigDecimal actualAmount;
    private UUID walletId;
    private UUID categoryId;
    private LocalDate transactionDate;
    private String note;
}
