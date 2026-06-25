package com.moneyflowbackend.quickentry.dto;

import com.moneyflowbackend.transaction.model.TransactionStatus;
import com.moneyflowbackend.transaction.model.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuickEntryPreviewResponse {
    private String rawInput;
    private String normalizedInput;
    private TransactionType type;
    private TransactionStatus status;
    private BigDecimal amount;
    private UUID walletId;
    private String walletName;
    private UUID categoryId;
    private String categoryName;
    private UUID sourceWalletId;
    private String sourceWalletName;
    private UUID destinationWalletId;
    private String destinationWalletName;
    private LocalDate transactionDate;
    private LocalTime transactionTime;
    private String description;
    private String note;
    private double confidence;
    private boolean readyToConfirm;
    @Builder.Default
    private List<String> missingFields = new ArrayList<>();
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
    private String matchedKeyword;
    private UUID matchedCategoryId;
    private String matchedWalletText;
}
