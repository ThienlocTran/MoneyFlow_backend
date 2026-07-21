package com.moneyflowbackend.obligation.service;

import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.obligation.dto.ConfirmOccurrenceRequest;
import com.moneyflowbackend.obligation.dto.ConfirmOccurrenceResponse;
import com.moneyflowbackend.obligation.model.ObligationAmountMode;
import com.moneyflowbackend.obligation.model.ObligationDirection;
import com.moneyflowbackend.obligation.model.ObligationOccurrence;
import com.moneyflowbackend.obligation.model.ObligationOccurrenceStatus;
import com.moneyflowbackend.obligation.model.RecurringObligationTemplate;
import com.moneyflowbackend.obligation.repository.ObligationOccurrenceRepository;
import com.moneyflowbackend.transaction.dto.TransactionResponse;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.transaction.service.TransactionService;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ObligationConfirmationService {
    private static final int MAX_NOTE_LENGTH = 500;

    private final ObligationOccurrenceRepository occurrenceRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final TransactionService transactionService;
    private final ObligationOccurrenceMapper mapper;
    private final Clock clock;

    @Transactional
    public ConfirmOccurrenceResponse confirm(UUID workspaceId, UUID occurrenceId, ConfirmOccurrenceRequest request, UUID userId) {
        WorkspaceMember member = requireWritableMember(workspaceId, userId);
        requireExplicitConfirmation(request);

        ObligationOccurrence occurrence = lockedOccurrence(workspaceId, occurrenceId);
        if (occurrence.getStatus() == ObligationOccurrenceStatus.CONFIRMED) {
            if (occurrence.getLinkedTransaction() == null) {
                throw integrityError();
            }
            return response(member.getWorkspace(), occurrence,
                    transactionService.getDetails(workspaceId, occurrence.getLinkedTransaction().getId(), false, userId));
        }
        if (occurrence.getLinkedTransaction() != null) {
            throw integrityError();
        }
        if (occurrence.getStatus() != ObligationOccurrenceStatus.PENDING) {
            throw invalidState();
        }

        RecurringObligationTemplate template = occurrence.getTemplate();
        BigDecimal amount = resolveAmount(template, occurrence, request.getActualAmount());
        UUID walletId = request.getWalletId() != null
                ? request.getWalletId()
                : template.getDefaultWallet() == null ? null : template.getDefaultWallet().getId();
        UUID categoryId = request.getCategoryId() != null
                ? request.getCategoryId()
                : template.getDefaultCategory() == null ? null : template.getDefaultCategory().getId();
        if (walletId == null) {
            throw new BusinessException("WALLET_REQUIRED", "Wallet is required");
        }
        if (categoryId == null) {
            throw new BusinessException("CATEGORY_REQUIRED", "Category is required");
        }

        TransactionType type = template.getDirection() == ObligationDirection.PAYABLE
                ? TransactionType.EXPENSE
                : TransactionType.INCOME;
        LocalDate transactionDate = request.getTransactionDate() != null
                ? request.getTransactionDate()
                : workspaceToday(member.getWorkspace());
        String description = defaultDescription(template, occurrence);
        String note = normalizeNote(request.getNote());
        TransactionResponse transaction = transactionService.createConfirmedObligationTransaction(
                workspaceId,
                type,
                amount,
                walletId,
                categoryId,
                transactionDate,
                description,
                note,
                userId);

        Instant now = clock.instant();
        occurrence.setStatus(ObligationOccurrenceStatus.CONFIRMED);
        occurrence.setActualAmount(amount);
        occurrence.setLinkedTransaction(transactionService.reference(workspaceId, transaction.getId()));
        occurrence.setCompletedAt(now);
        occurrence.setSnoozedUntil(null);
        occurrence.setSkippedAt(null);
        occurrence.setSkipReason(null);
        occurrence.setUpdatedAt(now);
        occurrence = occurrenceRepository.saveAndFlush(occurrence);
        return response(member.getWorkspace(), occurrence, transaction);
    }

    private WorkspaceMember requireWritableMember(UUID workspaceId, UUID userId) {
        workspaceRepository.findById(workspaceId)
                .filter(workspace -> workspace.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException("WORKSPACE_NOT_FOUND", "Workspace not found", HttpStatus.NOT_FOUND));
        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserIdAndMemberStatus(workspaceId, userId, "ACTIVE")
                .orElseThrow(() -> new BusinessException("WORKSPACE_ACCESS_DENIED", "Workspace access denied", HttpStatus.FORBIDDEN));
        if (member.getRole() == WorkspaceRole.VIEWER) {
            throw new BusinessException("FORBIDDEN", "Viewer cannot confirm obligation occurrences", HttpStatus.FORBIDDEN);
        }
        return member;
    }

    private void requireExplicitConfirmation(ConfirmOccurrenceRequest request) {
        if (request == null || request.getConfirmed() == null || !request.getConfirmed()) {
            throw new BusinessException("CONFIRMATION_REQUIRED", "confirmed must be true");
        }
    }

    private ObligationOccurrence lockedOccurrence(UUID workspaceId, UUID occurrenceId) {
        return occurrenceRepository.findByIdAndWorkspaceId(occurrenceId, workspaceId)
                .orElseThrow(() -> new BusinessException("OBLIGATION_OCCURRENCE_NOT_FOUND", "Obligation occurrence not found", HttpStatus.NOT_FOUND));
    }

    private BigDecimal resolveAmount(RecurringObligationTemplate template, ObligationOccurrence occurrence, BigDecimal actualAmount) {
        BigDecimal amount = actualAmount;
        if (amount == null && template.getAmountMode() == ObligationAmountMode.FIXED) {
            amount = occurrence.getExpectedAmount();
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_OBLIGATION_AMOUNT", "Amount must be greater than 0");
        }
        return amount;
    }

    private String normalizeNote(String note) {
        if (note == null) {
            return null;
        }
        String value = note.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > MAX_NOTE_LENGTH) {
            throw new BusinessException("VALIDATION_ERROR", "note must be at most " + MAX_NOTE_LENGTH + " characters");
        }
        return value;
    }

    private String defaultDescription(RecurringObligationTemplate template, ObligationOccurrence occurrence) {
        String description = template.getName() + " " + occurrence.getDueDate();
        return description.length() <= 500 ? description : description.substring(0, 500);
    }

    private ConfirmOccurrenceResponse response(Workspace workspace, ObligationOccurrence occurrence, TransactionResponse transaction) {
        return ConfirmOccurrenceResponse.builder()
                .occurrence(mapper.toResponse(occurrence, workspaceToday(workspace)))
                .transaction(transaction)
                .build();
    }

    private BusinessException invalidState() {
        return new BusinessException("INVALID_OCCURRENCE_STATE", "Occurrence cannot be confirmed in its current state", HttpStatus.CONFLICT);
    }

    private BusinessException integrityError() {
        return new BusinessException("OBLIGATION_CONFIRMATION_INTEGRITY_ERROR", "Obligation occurrence confirmation state is invalid", HttpStatus.CONFLICT);
    }

    private LocalDate workspaceToday(Workspace workspace) {
        try {
            return LocalDate.now(clock.withZone(ZoneId.of(workspace.getTimezone())));
        } catch (DateTimeException ex) {
            throw new BusinessException("INVALID_WORKSPACE_TIMEZONE", "Workspace timezone is invalid");
        }
    }
}
