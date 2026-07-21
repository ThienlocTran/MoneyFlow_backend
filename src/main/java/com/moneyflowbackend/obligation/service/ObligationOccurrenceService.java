package com.moneyflowbackend.obligation.service;

import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.obligation.dto.ObligationOccurrenceResponse;
import com.moneyflowbackend.obligation.dto.SkipOccurrenceRequest;
import com.moneyflowbackend.obligation.dto.SnoozeOccurrenceRequest;
import com.moneyflowbackend.obligation.model.ObligationOccurrence;
import com.moneyflowbackend.obligation.model.ObligationOccurrenceStatus;
import com.moneyflowbackend.obligation.repository.ObligationOccurrenceRepository;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ObligationOccurrenceService {
    private static final int MAX_REASON_LENGTH = 500;

    private final ObligationOccurrenceRepository occurrenceRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ObligationOccurrenceMapper mapper;
    private final Clock clock;

    @Transactional
    public ObligationOccurrenceResponse skip(UUID workspaceId, UUID occurrenceId, SkipOccurrenceRequest request, UUID userId) {
        WorkspaceMember member = requireWritableMember(workspaceId, userId);
        ObligationOccurrence occurrence = lockedOccurrence(workspaceId, occurrenceId);
        if (occurrence.getStatus() == ObligationOccurrenceStatus.SKIPPED) {
            return mapper.toResponse(occurrence, workspaceToday(member.getWorkspace()));
        }
        requirePending(occurrence, "skip");
        occurrence.setStatus(ObligationOccurrenceStatus.SKIPPED);
        occurrence.setSkippedAt(Instant.now(clock));
        occurrence.setSkipReason(normalizeReason(request == null ? null : request.getReason()));
        occurrence.setSnoozedUntil(null);
        occurrence.setUpdatedAt(Instant.now(clock));
        return mapper.toResponse(occurrence, workspaceToday(member.getWorkspace()));
    }

    @Transactional
    public ObligationOccurrenceResponse snooze(UUID workspaceId, UUID occurrenceId, SnoozeOccurrenceRequest request, UUID userId) {
        WorkspaceMember member = requireWritableMember(workspaceId, userId);
        LocalDate today = workspaceToday(member.getWorkspace());
        LocalDate snoozedUntil = request == null ? null : request.getSnoozedUntil();
        if (snoozedUntil == null || !snoozedUntil.isAfter(today)) {
            throw new BusinessException("INVALID_SNOOZE_DATE", "snoozedUntil must be after workspace today");
        }
        ObligationOccurrence occurrence = lockedOccurrence(workspaceId, occurrenceId);
        requirePending(occurrence, "snooze");
        if (!snoozedUntil.equals(occurrence.getSnoozedUntil())) {
            occurrence.setSnoozedUntil(snoozedUntil);
            occurrence.setUpdatedAt(Instant.now(clock));
        }
        return mapper.toResponse(occurrence, today);
    }

    @Transactional
    public ObligationOccurrenceResponse reopen(UUID workspaceId, UUID occurrenceId, UUID userId) {
        WorkspaceMember member = requireWritableMember(workspaceId, userId);
        ObligationOccurrence occurrence = lockedOccurrence(workspaceId, occurrenceId);
        if (occurrence.getStatus() == ObligationOccurrenceStatus.PENDING) {
            return mapper.toResponse(occurrence, workspaceToday(member.getWorkspace()));
        }
        if (occurrence.getStatus() != ObligationOccurrenceStatus.SKIPPED) {
            throw invalidState("reopen");
        }
        occurrence.setStatus(ObligationOccurrenceStatus.PENDING);
        occurrence.setSkippedAt(null);
        occurrence.setSkipReason(null);
        occurrence.setSnoozedUntil(null);
        occurrence.setUpdatedAt(Instant.now(clock));
        return mapper.toResponse(occurrence, workspaceToday(member.getWorkspace()));
    }

    private ObligationOccurrence lockedOccurrence(UUID workspaceId, UUID occurrenceId) {
        return occurrenceRepository.findByIdAndWorkspaceId(occurrenceId, workspaceId)
                .orElseThrow(() -> new BusinessException("OBLIGATION_OCCURRENCE_NOT_FOUND", "Obligation occurrence not found", HttpStatus.NOT_FOUND));
    }

    private WorkspaceMember requireWritableMember(UUID workspaceId, UUID userId) {
        WorkspaceMember member = requireActiveMember(workspaceId, userId);
        if (member.getRole() == WorkspaceRole.VIEWER) {
            throw new BusinessException("FORBIDDEN", "Viewer cannot modify obligation occurrences", HttpStatus.FORBIDDEN);
        }
        return member;
    }

    private WorkspaceMember requireActiveMember(UUID workspaceId, UUID userId) {
        workspaceRepository.findById(workspaceId)
                .filter(ws -> ws.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException("WORKSPACE_NOT_FOUND", "Workspace not found", HttpStatus.NOT_FOUND));
        return workspaceMemberRepository.findByWorkspaceIdAndUserIdAndMemberStatus(workspaceId, userId, "ACTIVE")
                .orElseThrow(() -> new BusinessException("WORKSPACE_ACCESS_DENIED", "Workspace access denied", HttpStatus.FORBIDDEN));
    }

    private void requirePending(ObligationOccurrence occurrence, String action) {
        if (occurrence.getStatus() != ObligationOccurrenceStatus.PENDING) {
            throw invalidState(action);
        }
    }

    private BusinessException invalidState(String action) {
        return new BusinessException("INVALID_OCCURRENCE_STATE", "Occurrence cannot be " + action + "ed in its current state", HttpStatus.CONFLICT);
    }

    private LocalDate workspaceToday(Workspace workspace) {
        try {
            return LocalDate.now(clock.withZone(ZoneId.of(workspace.getTimezone())));
        } catch (DateTimeException ex) {
            throw new BusinessException("INVALID_WORKSPACE_TIMEZONE", "Workspace timezone is invalid");
        }
    }

    private String normalizeReason(String reason) {
        if (reason == null) {
            return null;
        }
        String value = reason.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > MAX_REASON_LENGTH) {
            throw new BusinessException("VALIDATION_ERROR", "reason must be at most " + MAX_REASON_LENGTH + " characters");
        }
        return value;
    }
}
