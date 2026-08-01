package com.moneyflowbackend.voice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceQueryResponse {
    private String mode; // "READ_ONLY_QUERY"
    private String intent; // TODAY_EXPENSE_TOTAL, MONTH_EXPENSE_TOTAL, etc.
    private String status; // ANSWERED, NEEDS_CLARIFICATION, UNSUPPORTED
    private String question;
    private String answerText;
    private PeriodDto period;
    private String currency; // "VND"
    @Builder.Default
    private List<MetricDto> metrics = new ArrayList<>();
    @Builder.Default
    private List<BreakdownDto> breakdowns = new ArrayList<>();
    @Builder.Default
    private List<LinkDto> links = new ArrayList<>();
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PeriodDto {
        private String label;
        private LocalDate from;
        private LocalDate to;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MetricDto {
        private String key;
        private String label;
        private BigDecimal amount;
        private Long count;

        public MetricDto(String key, String label, BigDecimal amount) {
            this.key = key;
            this.label = label;
            this.amount = amount;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BreakdownDto {
        private String key;
        private String label;
        @Builder.Default
        private List<BreakdownItemDto> items = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BreakdownItemDto {
        private String label;
        private BigDecimal amount;
        private long count;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LinkDto {
        private String label;
        private String route;
    }
}
