package com.moneyflowbackend.quickentry.dto;

import com.moneyflowbackend.transaction.model.TransactionStatus;
import com.moneyflowbackend.transaction.model.TransactionType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
public class QuickEntryConfirmRequest {
    private String rawInput;
    private TransactionType type;
    private TransactionStatus status;
    private BigDecimal amount;
    private UUID walletId;
    private UUID categoryId;
    private UUID sourceWalletId;
    private UUID destinationWalletId;
    private LocalDate transactionDate;
    private LocalTime transactionTime;
    private String description;
    private String note;
    private UUID attributedPersonId;
    private String learnKeyword;
    private Integer durationSeconds;
    private String audioMimeType;

}
