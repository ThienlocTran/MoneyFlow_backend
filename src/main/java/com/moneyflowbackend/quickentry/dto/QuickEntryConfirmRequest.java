package com.moneyflowbackend.quickentry.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.moneyflowbackend.common.model.SpendingScope;
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
    private SpendingScope spendingScope;
    @JsonIgnore
    private boolean spendingScopeSet;
    private UUID attributedPersonId;
    private String learnKeyword;
    private Integer durationSeconds;
    private String audioMimeType;

    public void setSpendingScope(SpendingScope spendingScope) {
        this.spendingScope = spendingScope;
        this.spendingScopeSet = true;
    }

    @JsonIgnore
    public boolean hasSpendingScope() {
        return spendingScopeSet;
    }
}
