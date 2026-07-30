package com.moneyflowbackend.voice.dto;

import com.moneyflowbackend.common.model.SpendingScope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceReviewDraftResponse {
    private UUID voiceRecordId;
    private String transcript;
    private String status;
    private String confidence;
    private Candidate candidate;
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
    @Builder.Default
    private List<Suggestion> suggestions = new ArrayList<>();
    private String audioStatus;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Candidate {
        private VoiceReviewDraftType type;
        private BigDecimal amount;
        private String currency;
        private OffsetDateTime occurredAt;
        private UUID walletId;
        private String walletName;
        private UUID categoryId;
        private String categoryName;
        private UUID incomeSourceId;
        private String incomeSourceName;
        private String note;
        private SpendingScope scope;
        private boolean affectsWalletBalance;
        @Builder.Default
        private List<String> needsFields = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Suggestion {
        private String field;
        private String reason;
        private String message;
    }
}
