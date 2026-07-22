package com.moneyflowbackend.quickentry.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.moneyflowbackend.common.model.SpendingScope;
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
    private SpendingScope spendingScope;
    @JsonIgnore
    private boolean spendingScopeSet;
    private UUID attributedPersonId;

    public void setSpendingScope(SpendingScope spendingScope) {
        this.spendingScope = spendingScope;
        this.spendingScopeSet = true;
    }

    @JsonIgnore
    public boolean hasSpendingScope() {
        return spendingScopeSet;
    }
}
