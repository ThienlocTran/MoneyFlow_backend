package com.moneyflowbackend.planning.service;

import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.planning.dto.CancelPlannedObligationRequest;
import com.moneyflowbackend.planning.dto.LinkPlannedObligationTransactionRequest;
import com.moneyflowbackend.planning.dto.MarkPlannedObligationPaidRequest;
import com.moneyflowbackend.planning.dto.PlannedObligationListResponse;
import com.moneyflowbackend.planning.dto.PlannedObligationMarkPaidResponse;
import com.moneyflowbackend.planning.dto.PlannedObligationRequest;
import com.moneyflowbackend.planning.dto.PlannedObligationResponse;
import com.moneyflowbackend.planning.dto.PlannedObligationUpdateRequest;
import com.moneyflowbackend.planning.model.PlannedObligation;
import com.moneyflowbackend.planning.model.PlannedObligationComputedState;
import com.moneyflowbackend.planning.model.PlannedObligationPriority;
import com.moneyflowbackend.planning.model.PlannedObligationStatus;
import com.moneyflowbackend.planning.model.PlanningRecurrenceType;
import com.moneyflowbackend.planning.repository.PlannedObligationRepository;
import com.moneyflowbackend.transaction.dto.TransactionResponse;
import com.moneyflowbackend.transaction.model.Transaction;
import com.moneyflowbackend.transaction.model.TransactionStatus;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.transaction.service.TransactionService;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.repository.WalletRepository;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.service.WorkspaceService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Service
public class PlannedObligationService {
    private static final int LIST_LIMIT = 100;

    private final PlannedObligationRepository obligationRepository;
    private final WorkspaceService workspaceService;
    private final WalletRepository walletRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final Clock clock;

    public PlannedObligationService(PlannedObligationRepository obligationRepository,
                                    WorkspaceService workspaceService,
                                    WalletRepository walletRepository,
                                    CategoryRepository categoryRepository,
                                    TransactionRepository transactionRepository,
                                    TransactionService transactionService,
                                    Clock clock) {
        this.obligationRepository = obligationRepository;
        this.workspaceService = workspaceService;
        this.walletRepository = walletRepository;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.transactionService = transactionService;
        this.clock = clock;
    }

    @Transactional
    public PlannedObligationResponse create(UUID workspaceId, PlannedObligationRequest request, UUID userId) {
        WorkspaceMember member = workspaceService.requireWritableMember(workspaceId, userId);
        PlannedObligation obligation = PlannedObligation.builder()
                .workspace(member.getWorkspace())
                .createdByUser(member.getUser())
                .name(requiredName(request == null ? null : request.name()))
                .amount(requiredAmount(request == null ? null : request.amount()))
                .currency(currency(request == null ? null : request.currency()))
                .dueDate(requiredDate(request == null ? null : request.dueDate()))
                .priority(parsePriority(request == null ? null : request.priority()))
                .recurrenceType(parseRecurrence(request == null ? null : request.recurrenceType()))
                .wallet(wallet(workspaceId, request == null ? null : request.walletId()))
                .category(category(workspaceId, request == null ? null : request.categoryId()))
                .note(note(request == null ? null : request.note()))
                .build();
        return map(obligationRepository.saveAndFlush(obligation));
    }

    @Transactional(readOnly = true)
    public PlannedObligationListResponse list(UUID workspaceId, LocalDate from, LocalDate to, String status, String priority,
                                              boolean includeCancelled, UUID userId) {
        workspaceService.verifyMembership(workspaceId, userId);
        validateRange(from, to);
        var rows = obligationRepository.search(
                workspaceId,
                from,
                to,
                parseOptionalStatus(status),
                parseOptionalPriority(priority),
                includeCancelled,
                PageRequest.of(0, LIST_LIMIT));
        return new PlannedObligationListResponse(rows.stream().map(this::map).toList(), rows.size(), LIST_LIMIT);
    }

    @Transactional(readOnly = true)
    public PlannedObligationResponse get(UUID workspaceId, UUID obligationId, UUID userId) {
        workspaceService.verifyMembership(workspaceId, userId);
        return map(obligation(workspaceId, obligationId));
    }

    @Transactional
    public PlannedObligationResponse update(UUID workspaceId, UUID obligationId, PlannedObligationUpdateRequest request, UUID userId) {
        workspaceService.requireWritableMember(workspaceId, userId);
        PlannedObligation obligation = obligation(workspaceId, obligationId);
        if (obligation.getStatus() == PlannedObligationStatus.CANCELLED) {
            throw new BusinessException("PLANNED_OBLIGATION_CANCELLED", "Cancelled obligation cannot be updated");
        }
        if (request == null) {
            return map(obligation);
        }
        if (request.name() != null) obligation.setName(requiredName(request.name()));
        if (request.amount() != null) obligation.setAmount(requiredAmount(request.amount()));
        if (request.currency() != null) obligation.setCurrency(currency(request.currency()));
        if (request.dueDate() != null) obligation.setDueDate(requiredDate(request.dueDate()));
        if (request.priority() != null) obligation.setPriority(parsePriority(request.priority()));
        if (request.recurrenceType() != null) obligation.setRecurrenceType(parseRecurrence(request.recurrenceType()));
        if (request.walletId() != null) obligation.setWallet(wallet(workspaceId, request.walletId()));
        if (request.categoryId() != null) obligation.setCategory(category(workspaceId, request.categoryId()));
        if (request.note() != null) obligation.setNote(note(request.note()));
        return map(obligationRepository.saveAndFlush(obligation));
    }

    @Transactional
    public PlannedObligationResponse cancel(UUID workspaceId, UUID obligationId, CancelPlannedObligationRequest request, UUID userId) {
        workspaceService.requireWritableMember(workspaceId, userId);
        PlannedObligation obligation = obligation(workspaceId, obligationId);
        if (obligation.getStatus() == PlannedObligationStatus.PAID) {
            throw new BusinessException("PLANNED_OBLIGATION_PAID", "Paid obligation cannot be cancelled");
        }
        if (obligation.getStatus() == PlannedObligationStatus.CANCELLED) {
            return map(obligation);
        }
        obligation.setStatus(PlannedObligationStatus.CANCELLED);
        obligation.setCancelReason(trim(request == null ? null : request.reason(), 500));
        obligation.setCancelledAt(Instant.now(clock));
        return map(obligationRepository.saveAndFlush(obligation));
    }

    @Transactional
    public PlannedObligationResponse linkTransaction(UUID workspaceId, UUID obligationId, LinkPlannedObligationTransactionRequest request, UUID userId) {
        workspaceService.requireWritableMember(workspaceId, userId);
        PlannedObligation obligation = obligation(workspaceId, obligationId);
        UUID transactionId = request == null ? null : request.transactionId();
        if (transactionId == null) {
            throw new BusinessException("PLANNING_OBLIGATION_TRANSACTION_REQUIRED", "Transaction id is required");
        }
        Transaction transaction = transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)
                .orElseThrow(() -> new BusinessException("PLANNING_OBLIGATION_TRANSACTION_NOT_FOUND", "Transaction not found", HttpStatus.NOT_FOUND));
        if (obligation.getStatus() == PlannedObligationStatus.PAID) {
            if (obligation.getLinkedTransaction() != null && obligation.getLinkedTransaction().getId().equals(transactionId)) {
                return map(obligation);
            }
            throw new BusinessException("PLANNING_OBLIGATION_ALREADY_PAID", "Planned obligation is already paid");
        }
        validatePayable(obligation);
        validateLinkableTransaction(workspaceId, obligation, transaction);
        obligation.setStatus(PlannedObligationStatus.PAID);
        obligation.setLinkedTransaction(transaction);
        obligation.setPaidAt(request.paidAt() == null ? Instant.now(clock) : request.paidAt());
        obligation.setPaidNote(note(request.note()));
        return map(obligationRepository.saveAndFlush(obligation));
    }

    @Transactional
    public PlannedObligationMarkPaidResponse markPaid(UUID workspaceId, UUID obligationId, MarkPlannedObligationPaidRequest request, UUID userId) {
        workspaceService.requireWritableMember(workspaceId, userId);
        PlannedObligation obligation = obligation(workspaceId, obligationId);
        if (obligation.getStatus() == PlannedObligationStatus.PAID) {
            Transaction existing = obligation.getLinkedTransaction();
            return new PlannedObligationMarkPaidResponse(map(obligation), existing == null ? null : existing.getId(),
                    existing == null ? null : transactionService.mapExistingToResponse(existing));
        }
        validatePayable(obligation);
        if (request == null || request.walletId() == null) {
            throw new BusinessException("PLANNING_OBLIGATION_WALLET_REQUIRED", "Wallet is required to mark obligation paid");
        }
        if (request.categoryId() == null) {
            throw new BusinessException("PLANNING_OBLIGATION_CATEGORY_REQUIRED", "Category is required to mark obligation paid");
        }
        BigDecimal amount = request.amount() == null ? obligation.getAmount() : requiredAmount(request.amount());
        Wallet paidWallet = wallet(workspaceId, request.walletId());
        Category paidCategory = category(workspaceId, request.categoryId());
        TransactionResponse transaction = transactionService.createConfirmedObligationTransaction(
                workspaceId,
                TransactionType.EXPENSE,
                amount,
                paidWallet.getId(),
                paidCategory.getId(),
                request.transactionDate() == null ? LocalDate.now(clock) : request.transactionDate(),
                obligation.getName(),
                note(request.note()),
                userId);
        Transaction linked = transactionRepository.findByIdAndWorkspaceId(transaction.getId(), workspaceId)
                .orElseThrow(() -> new BusinessException("PLANNING_OBLIGATION_MARK_PAID_FAILED", "Created payment transaction could not be linked"));
        obligation.setStatus(PlannedObligationStatus.PAID);
        obligation.setLinkedTransaction(linked);
        obligation.setPaidAt(Instant.now(clock));
        obligation.setPaidNote(note(request.note()));
        PlannedObligation saved = obligationRepository.saveAndFlush(obligation);
        return new PlannedObligationMarkPaidResponse(map(saved), transaction.getId(), transaction);
    }

    private PlannedObligation obligation(UUID workspaceId, UUID obligationId) {
        return obligationRepository.findByIdAndWorkspaceIdAndDeletedAtIsNull(obligationId, workspaceId)
                .orElseThrow(() -> new BusinessException("PLANNED_OBLIGATION_NOT_FOUND", "Planned obligation not found", HttpStatus.NOT_FOUND));
    }

    private void validatePayable(PlannedObligation obligation) {
        if (obligation.getStatus() == PlannedObligationStatus.CANCELLED) {
            throw new BusinessException("PLANNING_OBLIGATION_CANCELLED", "Cancelled obligation cannot be paid");
        }
        if (obligation.getStatus() != PlannedObligationStatus.PLANNED) {
            throw new BusinessException("PLANNING_OBLIGATION_INVALID_STATE", "Planned obligation cannot be paid from current state");
        }
    }

    private void validateLinkableTransaction(UUID workspaceId, PlannedObligation obligation, Transaction transaction) {
        if (transaction.getDeletedAt() != null) {
            throw new BusinessException("PLANNING_OBLIGATION_TRANSACTION_DELETED", "Deleted transaction cannot be linked");
        }
        if (transaction.getTransactionStatus() != TransactionStatus.POSTED) {
            throw new BusinessException("PLANNING_OBLIGATION_TRANSACTION_NOT_POSTED", "Only posted transactions can be linked");
        }
        if (transaction.getTransactionType() != TransactionType.EXPENSE) {
            throw new BusinessException("PLANNING_OBLIGATION_TRANSACTION_TYPE_UNSUPPORTED", "Only expense transactions can be linked");
        }
        if (obligation.getAmount().compareTo(transaction.getAmount()) != 0) {
            throw new BusinessException("PLANNING_OBLIGATION_AMOUNT_MISMATCH", "Transaction amount must match obligation amount");
        }
        if (!obligation.getCurrency().equals(transaction.getCurrency())) {
            throw new BusinessException("PLANNING_OBLIGATION_CURRENCY_MISMATCH", "Transaction currency must match obligation currency");
        }
        if (obligationRepository.existsByWorkspaceIdAndLinkedTransactionIdAndDeletedAtIsNull(workspaceId, transaction.getId())) {
            throw new BusinessException("PLANNING_OBLIGATION_DUPLICATE_LINK", "Transaction is already linked to another obligation");
        }
    }

    private String requiredName(String raw) {
        String value = trim(raw, 120);
        if (value == null) {
            throw new BusinessException("PLANNED_OBLIGATION_NAME_REQUIRED", "Planned obligation name is required");
        }
        return value;
    }

    private BigDecimal requiredAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("PLANNED_OBLIGATION_AMOUNT_INVALID", "Planned obligation amount must be greater than zero");
        }
        return amount;
    }

    private LocalDate requiredDate(LocalDate date) {
        if (date == null) {
            throw new BusinessException("PLANNED_OBLIGATION_DUE_DATE_REQUIRED", "Planned obligation due date is required");
        }
        return date;
    }

    private String currency(String raw) {
        String value = raw == null || raw.isBlank() ? "VND" : raw.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z]{3}")) {
            throw new BusinessException("PLANNED_OBLIGATION_CURRENCY_INVALID", "Currency must use a 3-letter code");
        }
        return value;
    }

    private String note(String raw) {
        return trim(raw, 1000);
    }

    private String trim(String raw, int max) {
        if (raw == null) return null;
        String value = raw.trim().replaceAll("\\s+", " ");
        if (value.isEmpty()) return null;
        return value.length() > max ? value.substring(0, max) : value;
    }

    private PlannedObligationPriority parsePriority(String raw) {
        return raw == null || raw.isBlank() ? PlannedObligationPriority.REQUIRED : parseOptionalPriority(raw);
    }

    private PlannedObligationPriority parseOptionalPriority(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return PlannedObligationPriority.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("PLANNED_OBLIGATION_PRIORITY_INVALID", "Planned obligation priority is invalid");
        }
    }

    private PlannedObligationStatus parseOptionalStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return PlannedObligationStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("PLANNED_OBLIGATION_STATUS_INVALID", "Planned obligation status is invalid");
        }
    }

    private PlanningRecurrenceType parseRecurrence(String raw) {
        if (raw == null || raw.isBlank()) return PlanningRecurrenceType.NONE;
        try {
            return PlanningRecurrenceType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("PLANNED_OBLIGATION_RECURRENCE_INVALID", "Planned obligation recurrence type is invalid");
        }
    }

    private Wallet wallet(UUID workspaceId, UUID walletId) {
        if (walletId == null) return null;
        return walletRepository.findByIdAndWorkspaceId(walletId, workspaceId)
                .orElseThrow(() -> new BusinessException("WALLET_NOT_FOUND", "Wallet is missing or inaccessible", HttpStatus.NOT_FOUND));
    }

    private Category category(UUID workspaceId, UUID categoryId) {
        if (categoryId == null) return null;
        return categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)
                .orElseThrow(() -> new BusinessException("CATEGORY_NOT_FOUND", "Category is missing or inaccessible", HttpStatus.NOT_FOUND));
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new BusinessException("PLANNED_OBLIGATION_DATE_RANGE_INVALID", "Planning obligation date range is invalid");
        }
    }

    private PlannedObligationResponse map(PlannedObligation obligation) {
        Category category = obligation.getCategory();
        var jar = category == null ? null : category.getJar();
        Wallet wallet = obligation.getWallet();
        return new PlannedObligationResponse(
                obligation.getId(),
                obligation.getWorkspace().getId(),
                obligation.getName(),
                obligation.getAmount(),
                obligation.getCurrency(),
                obligation.getDueDate(),
                obligation.getStatus(),
                computedState(obligation),
                obligation.getPriority(),
                wallet == null ? null : wallet.getId(),
                wallet == null ? null : wallet.getName(),
                category == null ? null : category.getId(),
                category == null ? null : category.getName(),
                jar == null ? null : jar.getId(),
                jar == null ? null : jar.getName(),
                obligation.getNote(),
                obligation.getRecurrenceType(),
                obligation.getLinkedTransaction() == null ? null : obligation.getLinkedTransaction().getId(),
                obligation.getPaidAt(),
                obligation.getPaidNote(),
                obligation.getCreatedAt(),
                obligation.getUpdatedAt());
    }

    private PlannedObligationComputedState computedState(PlannedObligation obligation) {
        if (obligation.getStatus() == PlannedObligationStatus.PAID) return PlannedObligationComputedState.PAID;
        if (obligation.getStatus() == PlannedObligationStatus.CANCELLED) return PlannedObligationComputedState.CANCELLED;
        LocalDate today = LocalDate.now(clock);
        if (obligation.getDueDate().isBefore(today)) return PlannedObligationComputedState.OVERDUE;
        if (!obligation.getDueDate().isAfter(today.plusDays(7))) return PlannedObligationComputedState.DUE_SOON;
        return PlannedObligationComputedState.UPCOMING;
    }
}
