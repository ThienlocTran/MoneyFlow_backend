package com.moneyflowbackend.quickentry.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
public class QuickEntryButtonRequest {
    private UUID categoryId;
    private BigDecimal amount;
    private UUID walletId;
    private LocalDate transactionDate;
    private LocalTime transactionTime;
    private String description;
    private String note;
    private UUID attributedPersonId;

}
