package com.moneyflowbackend.quickentry.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.moneyflowbackend.common.model.SpendingScope;
import com.moneyflowbackend.transaction.model.TransactionStatus;
import com.moneyflowbackend.transaction.model.TransactionType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class QuickEntryBatchConfirmRequest {
    private String idempotencyKey;
    private String rawInput;
    private Integer durationSeconds;
    private String audioMimeType;
    private List<CandidateConfirmRequest> candidates = new ArrayList<>();

    @Data
    public static class CandidateConfirmRequest {
        private String candidateId;
        private String clientCandidateId;
        private Boolean selected;
        private VoiceIntentType intentType;
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

        public void setSpendingScope(SpendingScope spendingScope) {
            this.spendingScope = spendingScope;
            this.spendingScopeSet = true;
        }

        @JsonIgnore
        public boolean hasSpendingScope() {
            return spendingScopeSet;
        }
    }
}
