package com.moneyflowbackend.receipt.dto;

import com.moneyflowbackend.transaction.model.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceiptReviewParseResponse {
    private String mode;
    private String status;
    private ReceiptReviewSource source;
    private String rawText;
    private Candidate candidate;
    private Extracted extracted;
    @Builder.Default
    private List<Warning> warnings = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Candidate {
        private TransactionType type;
        private BigDecimal amount;
        private String currency;
        private OffsetDateTime occurredAt;
        private UUID walletId;
        private UUID categoryId;
        private String categoryName;
        private String merchantName;
        private String note;
        @Builder.Default
        private List<String> needsFields = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Extracted {
        private String merchantName;
        private LocalDate receiptDate;
        private BigDecimal totalAmount;
        @Builder.Default
        private List<BigDecimal> lineAmounts = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Warning {
        private String code;
        private String field;
        private String message;
    }
}
