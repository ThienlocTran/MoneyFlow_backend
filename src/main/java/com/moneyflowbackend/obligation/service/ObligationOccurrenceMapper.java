package com.moneyflowbackend.obligation.service;

import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.obligation.dto.FinancialInboxGroup;
import com.moneyflowbackend.obligation.dto.ObligationOccurrenceResponse;
import com.moneyflowbackend.obligation.dto.RecurringObligationTemplateResponse;
import com.moneyflowbackend.obligation.model.ObligationOccurrence;
import com.moneyflowbackend.obligation.model.ObligationOccurrenceStatus;
import com.moneyflowbackend.obligation.model.RecurringObligationTemplate;
import com.moneyflowbackend.wallet.model.Wallet;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
class ObligationOccurrenceMapper {
    ObligationOccurrenceResponse toResponse(ObligationOccurrence occurrence, LocalDate today) {
        RecurringObligationTemplate template = occurrence.getTemplate();
        return ObligationOccurrenceResponse.builder()
                .id(occurrence.getId())
                .templateId(template.getId())
                .templateName(template.getName())
                .workspaceId(occurrence.getWorkspace().getId())
                .direction(template.getDirection())
                .amountMode(template.getAmountMode())
                .expectedAmount(occurrence.getExpectedAmount())
                .actualAmount(occurrence.getActualAmount())
                .dueDate(occurrence.getDueDate())
                .reminderDate(occurrence.getReminderDate())
                .status(occurrence.getStatus())
                .inboxGroup(group(occurrence, today))
                .snoozedUntil(occurrence.getSnoozedUntil())
                .linkedTransactionId(occurrence.getLinkedTransaction() == null ? null : occurrence.getLinkedTransaction().getId())
                .defaultWallet(walletSummary(template.getDefaultWallet()))
                .defaultCategory(categorySummary(template.getDefaultCategory()))
                .note(template.getNote())
                .periodKey(occurrence.getPeriodKey())
                .skippedAt(occurrence.getSkippedAt())
                .skipReason(occurrence.getSkipReason())
                .completedAt(occurrence.getCompletedAt())
                .createdAt(occurrence.getCreatedAt())
                .updatedAt(occurrence.getUpdatedAt())
                .version(occurrence.getVersion())
                .build();
    }

    FinancialInboxGroup group(ObligationOccurrence occurrence, LocalDate today) {
        if (occurrence.getStatus() != ObligationOccurrenceStatus.PENDING || today == null) {
            return null;
        }
        if (occurrence.getSnoozedUntil() != null && occurrence.getSnoozedUntil().isAfter(today)) {
            return FinancialInboxGroup.SNOOZED;
        }
        if (occurrence.getDueDate().isBefore(today)) {
            return FinancialInboxGroup.OVERDUE;
        }
        if (occurrence.getDueDate().isEqual(today)) {
            return FinancialInboxGroup.DUE_TODAY;
        }
        return FinancialInboxGroup.UPCOMING;
    }

    private RecurringObligationTemplateResponse.ReferenceSummary walletSummary(Wallet wallet) {
        if (wallet == null) {
            return null;
        }
        return RecurringObligationTemplateResponse.ReferenceSummary.builder()
                .id(wallet.getId())
                .name(wallet.getName())
                .type(wallet.getWalletType().name())
                .active(wallet.isActive())
                .build();
    }

    private RecurringObligationTemplateResponse.ReferenceSummary categorySummary(Category category) {
        if (category == null) {
            return null;
        }
        return RecurringObligationTemplateResponse.ReferenceSummary.builder()
                .id(category.getId())
                .name(category.getName())
                .type(category.getCategoryType().name())
                .active(category.isActive())
                .archived(category.isArchived())
                .build();
    }
}
