package com.moneyflowbackend.transaction.dto;

import com.moneyflowbackend.transaction.model.TransactionStatus;
import com.moneyflowbackend.transaction.model.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
public class TransactionRequest {
    @NotNull(message = "Transaction type is required")
    private TransactionType type;

    private TransactionStatus status;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    private String currency;
    private UUID walletId;
    private UUID categoryId;
    private UUID attributedPersonId;
    private LocalDate transactionDate;
    private LocalTime transactionTime;
    private String description;
    private String note;
    private UUID sourceWalletId;
    private UUID destinationWalletId;
}
