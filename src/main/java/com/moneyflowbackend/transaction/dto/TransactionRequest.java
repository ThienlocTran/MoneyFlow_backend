package com.moneyflowbackend.transaction.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.moneyflowbackend.common.model.SpendingScope;
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
    private UUID incomeSourceId;
    private UUID relatedIncomeSourceId;
    private SpendingScope spendingScope;
    @JsonIgnore
    private boolean incomeSourceIdSet;
    @JsonIgnore
    private boolean relatedIncomeSourceIdSet;
    @JsonIgnore
    private boolean spendingScopeSet;
    private UUID attributedPersonId;
    private LocalDate transactionDate;
    private LocalTime transactionTime;
    private String description;
    private String note;
    private UUID sourceWalletId;
    private UUID destinationWalletId;

    public void setIncomeSourceId(UUID incomeSourceId) {
        this.incomeSourceId = incomeSourceId;
        this.incomeSourceIdSet = true;
    }

    public void setRelatedIncomeSourceId(UUID relatedIncomeSourceId) {
        this.relatedIncomeSourceId = relatedIncomeSourceId;
        this.relatedIncomeSourceIdSet = true;
    }

    public void setSpendingScope(SpendingScope spendingScope) {
        this.spendingScope = spendingScope;
        this.spendingScopeSet = true;
    }

    @JsonIgnore
    public boolean hasIncomeSourceId() {
        return incomeSourceIdSet;
    }

    @JsonIgnore
    public boolean hasRelatedIncomeSourceId() {
        return relatedIncomeSourceIdSet;
    }

    @JsonIgnore
    public boolean hasSpendingScope() {
        return spendingScopeSet;
    }
}
