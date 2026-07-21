package com.moneyflowbackend.obligation.service;

import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.obligation.model.ObligationAmountMode;
import com.moneyflowbackend.obligation.model.ObligationOccurrence;
import com.moneyflowbackend.obligation.model.ObligationOccurrenceStatus;
import com.moneyflowbackend.obligation.model.RecurringObligationStatus;
import com.moneyflowbackend.obligation.model.RecurringObligationTemplate;
import com.moneyflowbackend.obligation.repository.ObligationOccurrenceRepository;
import com.moneyflowbackend.obligation.repository.RecurringObligationTemplateRepository;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OccurrenceGenerationService {
    public static final int DEFAULT_FROM_DAYS_BEFORE = 31;
    public static final int DEFAULT_TO_DAYS_AFTER = 45;

    private final RecurrenceCalculator recurrenceCalculator;
    private final RecurringObligationTemplateRepository templateRepository;
    private final ObligationOccurrenceRepository occurrenceRepository;
    private final WorkspaceRepository workspaceRepository;
    private final Clock clock;

    @Transactional
    public OccurrenceGenerationResult generateForWorkspace(UUID workspaceId, LocalDate fromDate, LocalDate toDate) {
        validateWindow(fromDate, toDate);
        List<RecurringObligationTemplate> templates = templateRepository.findEligibleActiveForWorkspaceForUpdate(
                workspaceId,
                fromDate,
                toDate);

        int generatedCount = 0;
        int existingCount = 0;
        List<UUID> generatedIds = new ArrayList<>();
        for (RecurringObligationTemplate template : templates) {
            OccurrenceGenerationResult result = generateForLockedTemplate(template, fromDate, toDate);
            generatedCount += result.generatedCount();
            existingCount += result.existingCount();
            generatedIds.addAll(result.generatedOccurrenceIds());
        }
        return new OccurrenceGenerationResult(fromDate, toDate, generatedCount, existingCount, List.copyOf(generatedIds));
    }

    @Transactional
    public OccurrenceGenerationResult generateForWorkspaceDefaultWindow(UUID workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException("WORKSPACE_NOT_FOUND", "Workspace not found", HttpStatus.NOT_FOUND));
        LocalDate today = workspaceToday(workspace);
        return generateForWorkspace(
                workspaceId,
                today.minusDays(DEFAULT_FROM_DAYS_BEFORE),
                today.plusDays(DEFAULT_TO_DAYS_AFTER));
    }

    @Transactional
    public OccurrenceGenerationResult generateForTemplate(UUID workspaceId, UUID templateId, LocalDate fromDate, LocalDate toDate) {
        validateWindow(fromDate, toDate);
        RecurringObligationTemplate template = templateRepository.findByIdAndWorkspaceIdForUpdate(templateId, workspaceId)
                .orElseThrow(() -> new BusinessException("RECURRING_OBLIGATION_TEMPLATE_NOT_FOUND", "Recurring obligation template not found", HttpStatus.NOT_FOUND));
        if (template.getStatus() != RecurringObligationStatus.ACTIVE
                || template.getStartDate().isAfter(toDate)
                || (template.getEndDate() != null && template.getEndDate().isBefore(fromDate))) {
            return new OccurrenceGenerationResult(fromDate, toDate, 0, 0, List.of());
        }
        return generateForLockedTemplate(template, fromDate, toDate);
    }

    private OccurrenceGenerationResult generateForLockedTemplate(
            RecurringObligationTemplate template,
            LocalDate fromDate,
            LocalDate toDate) {
        validateTemplateState(template);
        List<LocalDate> dueDates = recurrenceCalculator.calculate(
                template.getStartDate(),
                template.getEndDate(),
                template.getFrequency(),
                template.getIntervalCount(),
                fromDate,
                toDate);
        if (dueDates.isEmpty()) {
            return new OccurrenceGenerationResult(fromDate, toDate, 0, 0, List.of());
        }

        Set<String> existingKeys = occurrenceRepository.findPeriodKeysByTemplateIdAndDueDateBetween(
                template.getId(),
                fromDate,
                toDate);
        List<ObligationOccurrence> missing = dueDates.stream()
                .filter(dueDate -> !existingKeys.contains(periodKey(dueDate)))
                .map(dueDate -> occurrence(template, dueDate))
                .toList();
        List<ObligationOccurrence> saved = occurrenceRepository.saveAll(missing);
        return new OccurrenceGenerationResult(
                fromDate,
                toDate,
                saved.size(),
                dueDates.size() - missing.size(),
                saved.stream().map(ObligationOccurrence::getId).toList());
    }

    private void validateWindow(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null) {
            throw new BusinessException("VALIDATION_ERROR", "Generation window is required");
        }
        if (fromDate.isAfter(toDate)) {
            throw new BusinessException("INVALID_DATE_RANGE", "fromDate must be before or equal to toDate");
        }
    }

    private void validateTemplateState(RecurringObligationTemplate template) {
        if (template.getIntervalCount() == null || template.getIntervalCount() < 1) {
            throw new BusinessException("INVALID_RECURRENCE_INTERVAL", "intervalCount must be at least 1");
        }
        if (template.getFrequency() == null) {
            throw new BusinessException("INVALID_RECURRENCE_FREQUENCY", "frequency is required");
        }
        if (template.getAmountMode() == ObligationAmountMode.FIXED && !positive(template.getDefaultAmount())) {
            throw new BusinessException("INVALID_RECURRING_OBLIGATION_AMOUNT", "Fixed obligation defaultAmount must be greater than 0");
        }
    }

    private ObligationOccurrence occurrence(RecurringObligationTemplate template, LocalDate dueDate) {
        return ObligationOccurrence.builder()
                .template(template)
                .workspace(template.getWorkspace())
                .periodKey(periodKey(dueDate))
                .dueDate(dueDate)
                .reminderDate(dueDate.minusDays(template.getReminderDaysBefore()))
                .expectedAmount(expectedAmount(template))
                .status(ObligationOccurrenceStatus.PENDING)
                .build();
    }

    private BigDecimal expectedAmount(RecurringObligationTemplate template) {
        if (template.getAmountMode() == ObligationAmountMode.FIXED) {
            return template.getDefaultAmount();
        }
        return positive(template.getDefaultAmount()) ? template.getDefaultAmount() : null;
    }

    private boolean positive(BigDecimal amount) {
        return amount != null && amount.signum() > 0;
    }

    private String periodKey(LocalDate dueDate) {
        return dueDate.toString();
    }

    private LocalDate workspaceToday(Workspace workspace) {
        try {
            return LocalDate.now(clock.withZone(ZoneId.of(workspace.getTimezone())));
        } catch (DateTimeException ex) {
            throw new BusinessException("INVALID_WORKSPACE_TIMEZONE", "Workspace timezone is invalid");
        }
    }
}
