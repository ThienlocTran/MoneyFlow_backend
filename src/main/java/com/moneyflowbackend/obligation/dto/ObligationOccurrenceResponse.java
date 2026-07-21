package com.moneyflowbackend.obligation.dto;

import com.moneyflowbackend.common.model.SpendingScope;
import com.moneyflowbackend.obligation.model.ObligationAmountMode;
import com.moneyflowbackend.obligation.model.ObligationDirection;
import com.moneyflowbackend.obligation.model.ObligationOccurrenceStatus;
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
public class ObligationOccurrenceResponse {
    private UUID id;
    private UUID templateId;
    private String templateName;
    private UUID workspaceId;
    private ObligationDirection direction;
    private ObligationAmountMode amountMode;
    private BigDecimal expectedAmount;
    private BigDecimal actualAmount;
    private LocalDate dueDate;
    private LocalDate reminderDate;
    private ObligationOccurrenceStatus status;
    private FinancialInboxGroup inboxGroup;
    private LocalDate snoozedUntil;
    private UUID linkedTransactionId;
    private RecurringObligationTemplateResponse.ReferenceSummary defaultWallet;
    private RecurringObligationTemplateResponse.ReferenceSummary defaultCategory;
    private SpendingScope spendingScope;
    private String note;
    private String periodKey;
    private Instant skippedAt;
    private String skipReason;
    private Instant completedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
