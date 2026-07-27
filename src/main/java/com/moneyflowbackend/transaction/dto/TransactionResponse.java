package com.moneyflowbackend.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.moneyflowbackend.common.model.SpendingScope;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionResponse {
    private UUID id;
    private String type;
    private String status;
    private BigDecimal amount;
    private String currency;
    private LocalDate transactionDate;
    private LocalTime transactionTime;
    private String description;
    private String note;
    private String rawInput;

    private UUID walletId;
    private String walletName;
    private UUID categoryId;
    private String categoryName;
    private SpendingScope spendingScope;
    private UUID incomeSourceId;
    private UUID relatedIncomeSourceId;
    private UUID sourceWalletId;
    private String sourceWalletName;
    private UUID destinationWalletId;
    private String destinationWalletName;

    private WalletRef wallet;
    private CategoryRef category;
    private TransferRef transfer;
    private PersonRef attributedPerson;
    private String sourceType;
    private UUID voiceRecordId;
    private boolean hasVoiceAudio;
    private boolean voiceAudioAvailable;
    private boolean playbackAvailable;
    private String audioMimeType;
    private Long audioSizeBytes;
    private Instant audioUploadedAt;
    private String voiceAudioStatus;
    private boolean historical;
    private boolean affectsWalletBalance;
    private boolean walletUnknown;
    private UserRef createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WalletRef {
        private UUID id;
        private String name;
        private String type;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CategoryRef {
        private UUID id;
        private String name;
        private String type;
        private UUID jarId;
        private String jarName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TransferRef {
        private UUID sourceWalletId;
        private String sourceWalletName;
        private UUID destinationWalletId;
        private String destinationWalletName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PersonRef {
        private UUID id;
        private String displayName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserRef {
        private UUID id;
        private String username;
        private String fullName;
    }
}
