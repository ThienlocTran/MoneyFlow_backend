package com.moneyflowbackend.obligation.service;

import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.obligation.dto.FinancialInboxGroup;
import com.moneyflowbackend.obligation.dto.FinancialInboxResponse;
import com.moneyflowbackend.obligation.dto.FinancialInboxSummaryResponse;
import com.moneyflowbackend.obligation.dto.ObligationOccurrencePageResponse;
import com.moneyflowbackend.obligation.dto.ObligationOccurrenceResponse;
import com.moneyflowbackend.obligation.model.ObligationDirection;
import com.moneyflowbackend.obligation.model.ObligationOccurrenceStatus;
import com.moneyflowbackend.obligation.repository.ObligationOccurrenceRepository;
import com.moneyflowbackend.obligation.repository.RecurringObligationTemplateRepository;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FinancialInboxService {
    private static final int DEFAULT_UPCOMING_DAYS = 30;

    private final OccurrenceGenerationService generationService;
    private final ObligationOccurrenceRepository occurrenceRepository;
    private final RecurringObligationTemplateRepository templateRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ObligationOccurrenceMapper mapper;
    private final Clock clock;

    @Transactional
    public FinancialInboxResponse inbox(
            UUID workspaceId,
            FinancialInboxGroup group,
            ObligationDirection direction,
            UUID templateId,
            LocalDate from,
            LocalDate to,
            int page,
            int size,
            UUID userId) {
        WorkspaceMember member = requireActiveMember(workspaceId, userId);
        validateDateRange(from, to);
        LocalDate today = workspaceToday(member.getWorkspace());
        generationService.generateForWorkspace(
                workspaceId,
                today.minusDays(OccurrenceGenerationService.DEFAULT_FROM_DAYS_BEFORE),
                today.plusDays(OccurrenceGenerationService.DEFAULT_TO_DAYS_AFTER));

        boolean dateFilter = from != null || to != null;
        LocalDate upcomingTo = today.plusDays(DEFAULT_UPCOMING_DAYS);
        Page<com.moneyflowbackend.obligation.model.ObligationOccurrence> occurrences = occurrenceRepository.findInboxPage(
                workspaceId,
                direction,
                templateId,
                groupName(group),
                dateFilter,
                from,
                to,
                today,
                upcomingTo,
                pageRequest(page, size));
        FinancialInboxSummaryResponse summary = summary(workspaceId, direction, templateId, dateFilter, from, to, today, upcomingTo);
        return FinancialInboxResponse.builder()
                .content(occurrences.getContent().stream().map(o -> mapper.toResponse(o, today)).toList())
                .page(occurrences.getNumber())
                .size(occurrences.getSize())
                .totalElements(occurrences.getTotalElements())
                .totalPages(occurrences.getTotalPages())
                .first(occurrences.isFirst())
                .last(occurrences.isLast())
                .summary(summary)
                .build();
    }

    @Transactional(readOnly = true)
    public ObligationOccurrencePageResponse history(
            UUID workspaceId,
            UUID templateId,
            ObligationOccurrenceStatus status,
            LocalDate from,
            LocalDate to,
            int page,
            int size,
            UUID userId) {
        WorkspaceMember member = requireActiveMember(workspaceId, userId);
        validateDateRange(from, to);
        if (!templateRepository.existsByIdAndWorkspaceId(templateId, workspaceId)) {
            throw new BusinessException("RECURRING_OBLIGATION_NOT_FOUND", "Recurring obligation not found", HttpStatus.NOT_FOUND);
        }
        LocalDate today = workspaceToday(member.getWorkspace());
        Page<com.moneyflowbackend.obligation.model.ObligationOccurrence> result = occurrenceRepository.findHistoryPage(
                workspaceId,
                templateId,
                status,
                from,
                to,
                pageRequest(page, size));
        return ObligationOccurrencePageResponse.builder()
                .content(result.getContent().stream().map(o -> mapper.toResponse(o, today)).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .first(result.isFirst())
                .last(result.isLast())
                .build();
    }

    private FinancialInboxSummaryResponse summary(
            UUID workspaceId,
            ObligationDirection direction,
            UUID templateId,
            boolean dateFilter,
            LocalDate from,
            LocalDate to,
            LocalDate today,
            LocalDate upcomingTo) {
        long overdue = count(workspaceId, direction, templateId, FinancialInboxGroup.OVERDUE, dateFilter, from, to, today, upcomingTo);
        long dueToday = count(workspaceId, direction, templateId, FinancialInboxGroup.DUE_TODAY, dateFilter, from, to, today, upcomingTo);
        long upcoming = count(workspaceId, direction, templateId, FinancialInboxGroup.UPCOMING, dateFilter, from, to, today, upcomingTo);
        long snoozed = count(workspaceId, direction, templateId, FinancialInboxGroup.SNOOZED, dateFilter, from, to, today, upcomingTo);
        return FinancialInboxSummaryResponse.builder()
                .overdueCount(overdue)
                .dueTodayCount(dueToday)
                .upcomingCount(upcoming)
                .snoozedCount(snoozed)
                .totalPendingCount(overdue + dueToday + upcoming + snoozed)
                .build();
    }

    private long count(
            UUID workspaceId,
            ObligationDirection direction,
            UUID templateId,
            FinancialInboxGroup group,
            boolean dateFilter,
            LocalDate from,
            LocalDate to,
            LocalDate today,
            LocalDate upcomingTo) {
        return occurrenceRepository.countInboxGroup(
                workspaceId,
                direction,
                templateId,
                group.name(),
                dateFilter,
                from,
                to,
                today,
                upcomingTo);
    }

    private WorkspaceMember requireActiveMember(UUID workspaceId, UUID userId) {
        workspaceRepository.findById(workspaceId)
                .filter(ws -> ws.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException("WORKSPACE_NOT_FOUND", "Workspace not found", HttpStatus.NOT_FOUND));
        return workspaceMemberRepository.findByWorkspaceIdAndUserIdAndMemberStatus(workspaceId, userId, "ACTIVE")
                .orElseThrow(() -> new BusinessException("WORKSPACE_ACCESS_DENIED", "Workspace access denied", HttpStatus.FORBIDDEN));
    }

    private LocalDate workspaceToday(Workspace workspace) {
        try {
            return LocalDate.now(clock.withZone(ZoneId.of(workspace.getTimezone())));
        } catch (DateTimeException ex) {
            throw new BusinessException("INVALID_WORKSPACE_TIMEZONE", "Workspace timezone is invalid");
        }
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException("INVALID_DATE_RANGE", "from must be before or equal to to");
        }
    }

    private PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    private String groupName(FinancialInboxGroup group) {
        return group == null ? null : group.name();
    }
}
