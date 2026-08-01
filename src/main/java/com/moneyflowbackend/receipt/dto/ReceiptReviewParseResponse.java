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
    private int imageCount;
    @Builder.Default
    private List<Attachment> attachments = new ArrayList<>();
    private Ocr ocr;
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
        private boolean affectsWalletBalance;
        @Builder.Default
        private List<String> needsFields = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Attachment {
        private int index;
        private String status;
        private String filename;
        private String contentType;
        private long sizeBytes;
        private String storageStatus;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Ocr {
        private String provider;
        private String status;
        private String text;
        @Builder.Default
        private List<OcrPage> pages = new ArrayList<>();
        @Builder.Default
        private List<Warning> warnings = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OcrPage {
        private int index;
        private String text;
        private Double confidence;
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
