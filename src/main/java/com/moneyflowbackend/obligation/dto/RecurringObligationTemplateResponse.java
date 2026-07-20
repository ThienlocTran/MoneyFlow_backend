package com.moneyflowbackend.obligation.dto;

import com.moneyflowbackend.obligation.model.ObligationAmountMode;
import com.moneyflowbackend.obligation.model.ObligationDirection;
import com.moneyflowbackend.obligation.model.ObligationFrequency;
import com.moneyflowbackend.obligation.model.RecurringObligationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringObligationTemplateResponse {
    private UUID id;
    private UUID workspaceId;
    private String name;
    private ObligationDirection direction;
    private ObligationAmountMode amountMode;
    private BigDecimal defaultAmount;
    private ObligationFrequency frequency;
    private Integer intervalCount;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer reminderDaysBefore;
    private ReferenceSummary defaultWallet;
    private ReferenceSummary defaultCategory;
    private String note;
    private RecurringObligationStatus status;
    private LocalDate nextDueDate;
    private boolean hasOccurrences;
    private UUID createdByUserId;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReferenceSummary {
        private UUID id;
        private String name;
        private String type;
        private Boolean active;
        private Boolean archived;
    }
}
