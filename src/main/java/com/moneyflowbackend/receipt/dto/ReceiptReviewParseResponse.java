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
        private boolean needsReview;
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
        private String merchantConfidence;
        private String merchantEvidence;
        private String dateConfidence;
        private String dateEvidence;
        private String categoryConfidence;
        private String categoryEvidence;
        @Builder.Default
        private List<BigDecimal> lineAmounts = new ArrayList<>();
        @Builder.Default
        private List<AmountCandidate> amountCandidates = new ArrayList<>();
        @Builder.Default
        private List<MerchantCandidate> merchantCandidates = new ArrayList<>();
        @Builder.Default
        private List<DateCandidate> dateCandidates = new ArrayList<>();
        @Builder.Default
        private List<CategoryCandidate> categoryCandidates = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AmountCandidate {
        private BigDecimal value;
        private String rawText;
        private String normalizedValue;
        private String lineText;
        private String nearbyLabel;
        private String source;
        private int score;
        private boolean selected;
        private boolean excluded;
        private String excludedReason;
        private String confidence;
        private String evidence;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MerchantCandidate {
        private String value;
        private String rawText;
        private String normalizedValue;
        private String source;
        private int score;
        private String confidence;
        private boolean selected;
        private boolean rejected;
        private String rejectedReason;
        private String evidence;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DateCandidate {
        private LocalDate value;
        private String rawText;
        private String lineText;
        private String source;
        private int score;
        private String confidence;
        private boolean selected;
        private boolean rejected;
        private String rejectedReason;
        private String evidence;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CategoryCandidate {
        private UUID categoryId;
        private String categoryName;
        private String source;
        private int score;
        private String confidence;
        private boolean selected;
        private boolean rejected;
        private String rejectedReason;
        private String evidence;
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
