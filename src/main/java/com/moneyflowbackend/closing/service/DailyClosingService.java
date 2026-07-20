package com.moneyflowbackend.closing.service;

import com.moneyflowbackend.closing.dto.CompleteDailyClosingRequest;
import com.moneyflowbackend.closing.dto.DailyClosingResponse;
import com.moneyflowbackend.closing.dto.DailyClosingWalletResponse;
import com.moneyflowbackend.closing.dto.ReconciliationAdjustmentRequest;
import com.moneyflowbackend.closing.dto.WalletSnapshotHistoryResponse;
import com.moneyflowbackend.closing.dto.WalletSnapshotPageResponse;
import com.moneyflowbackend.closing.dto.WalletSnapshotRequest;
import com.moneyflowbackend.closing.model.DailyClosing;
import com.moneyflowbackend.closing.model.DailyClosingStatus;
import com.moneyflowbackend.closing.repository.DailyClosingRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.transaction.model.AdjustmentDirection;
import com.moneyflowbackend.transaction.model.Transaction;
import com.moneyflowbackend.transaction.service.TransactionService;
import com.moneyflowbackend.wallet.model.BalanceSourceType;
import com.moneyflowbackend.wallet.model.ReconciliationStatus;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.model.WalletBalanceSnapshot;
import com.moneyflowbackend.wallet.repository.WalletBalanceSnapshotRepository;
import com.moneyflowbackend.wallet.repository.WalletRepository;
import com.moneyflowbackend.wallet.service.WalletBalanceService;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.service.WorkspaceService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class DailyClosingService {
    private static final int MAX_NOTE_LENGTH = 1000;

    private final DailyClosingRepository dailyClosingRepository;
    private final WalletBalanceSnapshotRepository snapshotRepository;
    private final WalletRepository walletRepository;
    private final WalletBalanceService walletBalanceService;
    private final WorkspaceService workspaceService;
    private final TransactionService transactionService;
    private final Clock clock;

    public DailyClosingService(
            DailyClosingRepository dailyClosingRepository,
            WalletBalanceSnapshotRepository snapshotRepository,
            WalletRepository walletRepository,
            WalletBalanceService walletBalanceService,
            WorkspaceService workspaceService,
            TransactionService transactionService,
            Clock clock) {
        this.dailyClosingRepository = dailyClosingRepository;
        this.snapshotRepository = snapshotRepository;
        this.walletRepository = walletRepository;
        this.walletBalanceService = walletBalanceService;
        this.workspaceService = workspaceService;
        this.transactionService = transactionService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DailyClosingResponse getDailyClosing(UUID workspaceId, LocalDate closingDate, UUID userId) {
        workspaceService.verifyMembership(workspaceId, userId);
        return dailyClosingRepository.findByWorkspaceIdAndClosingDate(workspaceId, closingDate)
                .map(closing -> mapDailyClosingResponse(workspaceId, closingDate, closing))
                .orElseGet(() -> mapDailyClosingResponse(workspaceId, closingDate, null));
    }

    @Transactional
    public DailyClosingResponse saveWalletSnapshot(UUID workspaceId, LocalDate closingDate, UUID walletId, WalletSnapshotRequest req, UUID userId) {
        WorkspaceMember member = workspaceService.requireWritableMember(workspaceId, userId);
        validateSnapshotRequest(req);
        Wallet wallet = walletRepository.findByIdAndWorkspaceId(walletId, workspaceId)
                .orElseThrow(() -> new BusinessException("WALLET_NOT_FOUND", "Khong tim thay vi", HttpStatus.NOT_FOUND));
        validateWritableWallet(wallet, closingDate);

        DailyClosing closing = findOrCreateOpenClosing(member, closingDate);
        if (closing.getStatus() == DailyClosingStatus.COMPLETED) {
            throw new BusinessException("DAILY_CLOSING_COMPLETED", "Phien chot so da hoan tat", HttpStatus.CONFLICT);
        }

        BigDecimal ledgerBalance = walletBalanceService.calculateBalanceAtEndOfDay(walletId, closingDate);
        BigDecimal difference = req.getActualBalance().subtract(ledgerBalance);
        Instant now = clock.instant();
        WalletBalanceSnapshot snapshot = snapshotRepository.findByDailyClosingIdAndWalletId(closing.getId(), walletId)
                .orElseGet(() -> WalletBalanceSnapshot.builder()
                        .workspace(member.getWorkspace())
                        .wallet(wallet)
                        .dailyClosing(closing)
                        .snapshotDate(closingDate)
                        .createdBy(member.getUser())
                        .createdAt(now)
                        .build());
        if (snapshot.getReconciliationStatus() == ReconciliationStatus.ADJUSTED) {
            throw new BusinessException("SNAPSHOT_ALREADY_ADJUSTED", "Snapshot da duoc dieu chinh", HttpStatus.CONFLICT);
        }

        snapshot.setBalance(req.getActualBalance());
        snapshot.setLedgerBalance(ledgerBalance);
        snapshot.setDifference(difference);
        snapshot.setReconciliationStatus(difference.compareTo(BigDecimal.ZERO) == 0
                ? ReconciliationStatus.MATCHED
                : ReconciliationStatus.UNRESOLVED);
        snapshot.setRecordedAt(req.getRecordedAt());
        snapshot.setSourceType(BalanceSourceType.MANUAL);
        snapshot.setNote(normalizeNote(req.getNote()));
        snapshot.setUpdatedAt(now);
        snapshotRepository.saveAndFlush(snapshot);

        return mapDailyClosingResponse(workspaceId, closingDate, closing);
    }

    @Transactional
    public DailyClosingResponse completeDailyClosing(UUID workspaceId, LocalDate closingDate, CompleteDailyClosingRequest req, UUID userId) {
        WorkspaceMember member = workspaceService.requireWritableMember(workspaceId, userId);
        DailyClosing closing = findOrCreateOpenClosing(member, closingDate);
        if (closing.getStatus() != DailyClosingStatus.COMPLETED) {
            closing.setStatus(DailyClosingStatus.COMPLETED);
            closing.setCompletedAt(clock.instant());
            closing.setCompletedBy(member.getUser());
            closing.setNote(normalizeNote(req == null ? null : req.getNote()));
            closing.setUpdatedAt(clock.instant());
            dailyClosingRepository.saveAndFlush(closing);
        }
        return mapDailyClosingResponse(workspaceId, closingDate, closing);
    }

    @Transactional
    public DailyClosingResponse reconcileWalletSnapshot(UUID workspaceId, UUID snapshotId, ReconciliationAdjustmentRequest req, UUID userId) {
        workspaceService.requireWritableMember(workspaceId, userId);
        WalletBalanceSnapshot snapshot = snapshotRepository.lockByIdAndWorkspaceId(snapshotId, workspaceId)
                .orElseThrow(() -> new BusinessException("SNAPSHOT_NOT_FOUND", "Snapshot not found", HttpStatus.NOT_FOUND));
        DailyClosing closing = snapshot.getDailyClosing();
        validateReconciliationSnapshot(snapshot, closing);
        validateAdjustmentRequest(snapshot, req);

        Transaction adjustment = transactionService.createAdjustment(
                workspaceId,
                snapshot.getWallet(),
                req.getDirection(),
                req.getAmount(),
                closing.getClosingDate(),
                normalizeNote(req.getNote()),
                userId,
                "wallet_snapshot:" + snapshot.getId());
        snapshot.setAdjustmentTransaction(adjustment);
        snapshot.setReconciliationStatus(ReconciliationStatus.ADJUSTED);
        snapshot.setUpdatedAt(clock.instant());
        snapshotRepository.saveAndFlush(snapshot);
        return mapDailyClosingResponse(workspaceId, closing.getClosingDate(), closing);
    }

    @Transactional(readOnly = true)
    public WalletSnapshotPageResponse listSnapshotHistory(
            UUID workspaceId,
            UUID walletId,
            LocalDate dateFrom,
            LocalDate dateTo,
            int page,
            int size,
            UUID userId) {
        workspaceService.verifyMembership(workspaceId, userId);
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new BusinessException("INVALID_DATE_RANGE", "Invalid date range");
        }
        if (walletId != null && walletRepository.findByIdAndWorkspaceId(walletId, workspaceId).isEmpty()) {
            throw new BusinessException("WALLET_NOT_FOUND", "Khong tim thay vi", HttpStatus.NOT_FOUND);
        }

        int pageNumber = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), 100);
        Page<WalletBalanceSnapshot> snapshots = snapshotRepository.findAll(
                historySpec(workspaceId, walletId, dateFrom, dateTo),
                PageRequest.of(pageNumber, pageSize, Sort.by(
                        Sort.Order.desc("snapshotDate"),
                        Sort.Order.desc("recordedAt"),
                        Sort.Order.desc("createdAt"))));

        return WalletSnapshotPageResponse.builder()
                .content(snapshots.getContent().stream().map(this::mapHistory).toList())
                .page(snapshots.getNumber())
                .size(snapshots.getSize())
                .totalElements(snapshots.getTotalElements())
                .totalPages(snapshots.getTotalPages())
                .first(snapshots.isFirst())
                .last(snapshots.isLast())
                .build();
    }

    private DailyClosing findOrCreateOpenClosing(WorkspaceMember member, LocalDate closingDate) {
        UUID workspaceId = member.getWorkspace().getId();
        return dailyClosingRepository.lockByWorkspaceIdAndClosingDate(workspaceId, closingDate)
                .orElseGet(() -> {
                    try {
                        return dailyClosingRepository.saveAndFlush(DailyClosing.builder()
                                .workspace(member.getWorkspace())
                                .closingDate(closingDate)
                                .status(DailyClosingStatus.OPEN)
                                .createdAt(clock.instant())
                                .updatedAt(clock.instant())
                                .build());
                    } catch (DataIntegrityViolationException ex) {
                        return dailyClosingRepository.lockByWorkspaceIdAndClosingDate(workspaceId, closingDate)
                                .orElseThrow(() -> ex);
                    }
                });
    }

    private DailyClosingResponse mapDailyClosingResponse(UUID workspaceId, LocalDate closingDate, DailyClosing closing) {
        List<WalletBalanceSnapshot> snapshots = closing == null ? List.of() : snapshotRepository.findAllByDailyClosingId(closing.getId());
        Map<UUID, Wallet> walletsById = new LinkedHashMap<>();
        walletRepository.findActiveOpenOnDate(workspaceId, closingDate).forEach(wallet -> walletsById.put(wallet.getId(), wallet));
        snapshots.forEach(snapshot -> walletsById.putIfAbsent(snapshot.getWallet().getId(), snapshot.getWallet()));

        Map<UUID, WalletBalanceSnapshot> snapshotsByWallet = new LinkedHashMap<>();
        snapshots.forEach(snapshot -> snapshotsByWallet.put(snapshot.getWallet().getId(), snapshot));
        Map<UUID, BigDecimal> ledgerBalances = walletBalanceService.calculateBalancesAtEndOfDay(walletsById.values(), closingDate);

        List<DailyClosingWalletResponse> walletResponses = walletsById.values().stream()
                .sorted(Comparator.comparing(Wallet::isActive, Comparator.reverseOrder())
                        .thenComparing(Wallet::getCreatedAt)
                        .thenComparing(w -> w.getName().toLowerCase(Locale.ROOT)))
                .map(wallet -> mapWallet(wallet, snapshotsByWallet.get(wallet.getId()), ledgerBalances.get(wallet.getId())))
                .toList();

        return DailyClosingResponse.builder()
                .closingId(closing == null ? null : closing.getId())
                .closingDate(closingDate)
                .status(closing == null ? DailyClosingStatus.OPEN.name() : closing.getStatus().name())
                .note(closing == null ? null : closing.getNote())
                .completedAt(closing == null ? null : closing.getCompletedAt())
                .completedByUserId(closing == null || closing.getCompletedBy() == null ? null : closing.getCompletedBy().getId())
                .wallets(walletResponses)
                .build();
    }

    private DailyClosingWalletResponse mapWallet(Wallet wallet, WalletBalanceSnapshot snapshot, BigDecimal ledgerBalance) {
        return DailyClosingWalletResponse.builder()
                .walletId(wallet.getId())
                .walletName(wallet.getName())
                .walletType(wallet.getWalletType().name())
                .ledgerBalance(snapshot == null ? ledgerBalance : snapshot.getLedgerBalance())
                .actualBalance(snapshot == null ? null : snapshot.getBalance())
                .difference(snapshot == null ? null : snapshot.getDifference())
                .snapshotId(snapshot == null ? null : snapshot.getId())
                .reconciliationStatus(snapshot == null || snapshot.getReconciliationStatus() == null ? null : snapshot.getReconciliationStatus().name())
                .sourceType(snapshot == null || snapshot.getSourceType() == null ? null : snapshot.getSourceType().name())
                .recordedAt(snapshot == null ? null : snapshot.getRecordedAt())
                .note(snapshot == null ? null : snapshot.getNote())
                .build();
    }

    private WalletSnapshotHistoryResponse mapHistory(WalletBalanceSnapshot snapshot) {
        return WalletSnapshotHistoryResponse.builder()
                .snapshotId(snapshot.getId())
                .dailyClosingId(snapshot.getDailyClosing() == null ? null : snapshot.getDailyClosing().getId())
                .walletId(snapshot.getWallet().getId())
                .walletName(snapshot.getWallet().getName())
                .snapshotDate(snapshot.getSnapshotDate())
                .recordedAt(snapshot.getRecordedAt())
                .actualBalance(snapshot.getBalance())
                .ledgerBalance(snapshot.getLedgerBalance())
                .difference(snapshot.getDifference())
                .reconciliationStatus(snapshot.getReconciliationStatus() == null ? null : snapshot.getReconciliationStatus().name())
                .sourceType(snapshot.getSourceType() == null ? null : snapshot.getSourceType().name())
                .note(snapshot.getNote())
                .createdAt(snapshot.getCreatedAt())
                .build();
    }

    private Specification<WalletBalanceSnapshot> historySpec(UUID workspaceId, UUID walletId, LocalDate dateFrom, LocalDate dateTo) {
        return (root, query, cb) -> {
            var predicate = cb.equal(root.get("workspace").get("id"), workspaceId);
            if (walletId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("wallet").get("id"), walletId));
            }
            if (dateFrom != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get("snapshotDate"), dateFrom));
            }
            if (dateTo != null) {
                predicate = cb.and(predicate, cb.lessThanOrEqualTo(root.get("snapshotDate"), dateTo));
            }
            return predicate;
        };
    }

    private void validateSnapshotRequest(WalletSnapshotRequest req) {
        if (req == null || req.getActualBalance() == null) {
            throw new BusinessException("INVALID_ACTUAL_BALANCE", "Actual balance is required");
        }
        if (req.getActualBalance().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("INVALID_ACTUAL_BALANCE", "Actual balance must not be negative");
        }
        if (req.getRecordedAt() == null) {
            throw new BusinessException("VALIDATION_ERROR", "Recorded time is required");
        }
        BalanceSourceType sourceType = parseSourceType(req.getSourceType());
        if (sourceType != BalanceSourceType.MANUAL) {
            throw new BusinessException("VALIDATION_ERROR", "Only MANUAL snapshots are accepted");
        }
        normalizeNote(req.getNote());
    }

    private BalanceSourceType parseSourceType(String raw) {
        if (raw == null || raw.isBlank()) {
            return BalanceSourceType.MANUAL;
        }
        try {
            return BalanceSourceType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("VALIDATION_ERROR", "Invalid source type");
        }
    }

    private void validateWritableWallet(Wallet wallet, LocalDate closingDate) {
        if (!wallet.isActive()) {
            throw new BusinessException("WALLET_INACTIVE", "Vi da ngung hoat dong", HttpStatus.CONFLICT);
        }
        if (wallet.getOpeningDate() != null && wallet.getOpeningDate().isAfter(closingDate)) {
            throw new BusinessException("WALLET_NOT_OPEN_ON_DATE", "Vi chua mo vao ngay chot so");
        }
    }

    private void validateReconciliationSnapshot(WalletBalanceSnapshot snapshot, DailyClosing closing) {
        if (snapshot.getSourceType() == BalanceSourceType.EXCEL_MIGRATION) {
            throw new BusinessException("HISTORICAL_SNAPSHOT_NOT_RECONCILABLE", "Historical snapshot cannot be reconciled", HttpStatus.CONFLICT);
        }
        if (closing == null || snapshot.getLedgerBalance() == null || snapshot.getDifference() == null) {
            throw new BusinessException("SNAPSHOT_NOT_RECONCILABLE", "Snapshot cannot be reconciled", HttpStatus.CONFLICT);
        }
        if (snapshot.getAdjustmentTransaction() != null || snapshot.getReconciliationStatus() == ReconciliationStatus.ADJUSTED) {
            throw new BusinessException("SNAPSHOT_ALREADY_ADJUSTED", "Snapshot already adjusted", HttpStatus.CONFLICT);
        }
        if (snapshot.getDifference().compareTo(BigDecimal.ZERO) == 0 || snapshot.getReconciliationStatus() == ReconciliationStatus.MATCHED) {
            throw new BusinessException("RECONCILIATION_NOT_NEEDED", "Snapshot is already matched", HttpStatus.CONFLICT);
        }
        if (snapshot.getReconciliationStatus() != ReconciliationStatus.UNRESOLVED) {
            throw new BusinessException("SNAPSHOT_NOT_RECONCILABLE", "Snapshot cannot be reconciled", HttpStatus.CONFLICT);
        }
        walletRepository.findByIdAndWorkspaceId(snapshot.getWallet().getId(), snapshot.getWorkspace().getId())
                .orElseThrow(() -> new BusinessException("SNAPSHOT_NOT_RECONCILABLE", "Snapshot wallet cannot be reconciled", HttpStatus.CONFLICT));
    }

    private void validateAdjustmentRequest(WalletBalanceSnapshot snapshot, ReconciliationAdjustmentRequest req) {
        if (req == null || !Boolean.TRUE.equals(req.getConfirmed())) {
            throw new BusinessException("CONFIRMATION_REQUIRED", "Confirmation is required");
        }
        if (req.getDirection() == null) {
            throw new BusinessException("INVALID_ADJUSTMENT_DIRECTION", "Adjustment direction is required");
        }
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_ADJUSTMENT_AMOUNT", "Adjustment amount must be greater than 0");
        }
        BigDecimal difference = snapshot.getDifference();
        AdjustmentDirection expectedDirection = difference.compareTo(BigDecimal.ZERO) > 0
                ? AdjustmentDirection.INCREASE
                : AdjustmentDirection.DECREASE;
        BigDecimal expectedAmount = difference.abs();
        if (req.getDirection() != expectedDirection) {
            throw new BusinessException("INVALID_ADJUSTMENT_DIRECTION", "Adjustment direction does not match difference");
        }
        if (req.getAmount().compareTo(expectedAmount) != 0) {
            throw new BusinessException("ADJUSTMENT_MISMATCH", "Adjustment amount does not match difference");
        }
        normalizeNote(req.getNote());
    }

    private String normalizeNote(String note) {
        String value = note == null ? null : note.trim();
        if (value != null && value.length() > MAX_NOTE_LENGTH) {
            throw new BusinessException("VALIDATION_ERROR", "Note is too long");
        }
        return value == null || value.isBlank() ? null : value;
    }
}
