package com.moneyflowbackend.closing.dto;

import com.moneyflowbackend.quickentry.dto.VoiceIntentType;
import com.moneyflowbackend.quickentry.dto.VoiceLedgerEffect;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyClosingVoicePreviewResponse {
    private UUID workspaceId;
    private LocalDate closingDate;
    private String transcript;
    private String candidateStatus;
    @Builder.Default
    private List<WalletCandidate> walletBalanceCandidates = new ArrayList<>();
    @Builder.Default
    private List<SkippedWallet> skippedWallets = new ArrayList<>();
    @Builder.Default
    private List<String> unmatchedSegments = new ArrayList<>();
    @Builder.Default
    private List<Warning> warnings = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WalletCandidate {
        private String candidateId;
        private String originalText;
        private String spokenWalletName;
        private UUID walletId;
        private String walletName;
        private BigDecimal actualBalance;
        private String currencyCode;
        private String status;
        private double confidence;
        private VoiceIntentType intentType;
        private VoiceLedgerEffect ledgerEffect;
        private String targetModule;
        @Builder.Default
        private List<String> inferenceNotes = new ArrayList<>();
        @Builder.Default
        private List<String> missingFields = new ArrayList<>();
        @Builder.Default
        private List<String> ambiguities = new ArrayList<>();
        @Builder.Default
        private List<WalletOption> walletOptions = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SkippedWallet {
        private String originalText;
        private String spokenWalletName;
        private UUID walletId;
        private String walletName;
        private String reason;
        @Builder.Default
        private List<WalletOption> walletOptions = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WalletOption {
        private UUID walletId;
        private String walletName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Warning {
        private String code;
        private String message;
        private String severity;
    }
}
