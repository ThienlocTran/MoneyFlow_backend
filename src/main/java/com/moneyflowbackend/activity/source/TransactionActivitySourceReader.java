package com.moneyflowbackend.activity.source;

import com.moneyflowbackend.activity.domain.ActivitySource;
import com.moneyflowbackend.activity.internal.ActivityCandidate;
import com.moneyflowbackend.activity.internal.ActivityTimelineOrdering;
import com.moneyflowbackend.activity.query.ActivityCursor;
import com.moneyflowbackend.activity.query.ActivityTimelineQuery;
import com.moneyflowbackend.obligation.repository.ObligationOccurrenceRepository;
import com.moneyflowbackend.transaction.audit.TransactionAuditLogRepository;
import com.moneyflowbackend.transaction.audit.TransactionAuditTimelineProjection;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.transaction.repository.TransferDetailRepository;
import com.moneyflowbackend.wallet.repository.WalletBalanceSnapshotRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TransactionActivitySourceReader implements ActivitySourceReader {
    private static final java.time.Instant MIN_OCCURRED_AT = java.time.Instant.parse("0001-01-01T00:00:00Z");
    private static final java.time.Instant MAX_OCCURRED_AT = java.time.Instant.parse("9999-12-31T23:59:59Z");
    private static final UUID MAX_UUID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

    private final TransactionAuditLogRepository auditLogRepository;
    private final TransactionRepository transactionRepository;
    private final TransferDetailRepository transferDetailRepository;
    private final ObligationOccurrenceRepository obligationOccurrenceRepository;
    private final WalletBalanceSnapshotRepository walletBalanceSnapshotRepository;
    private final TransactionActivityMapper mapper;

    public TransactionActivitySourceReader(
            TransactionAuditLogRepository auditLogRepository,
            TransactionRepository transactionRepository,
            TransferDetailRepository transferDetailRepository,
            ObligationOccurrenceRepository obligationOccurrenceRepository,
            WalletBalanceSnapshotRepository walletBalanceSnapshotRepository,
            TransactionActivityMapper mapper) {
        this.auditLogRepository = auditLogRepository;
        this.transactionRepository = transactionRepository;
        this.transferDetailRepository = transferDetailRepository;
        this.obligationOccurrenceRepository = obligationOccurrenceRepository;
        this.walletBalanceSnapshotRepository = walletBalanceSnapshotRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivityCandidate> read(ActivityTimelineQuery query, int limit) {
        ActivityCursor cursor = query.cursor();
        UUID cursorId = cursor != null && cursor.source() == ActivitySource.TRANSACTION_AUDIT
                ? UUID.fromString(cursor.stableId().substring((ActivitySource.TRANSACTION_AUDIT.name() + ":").length()))
                : MAX_UUID;
        List<TransactionAuditTimelineProjection> audits = auditLogRepository.findTimelinePage(
                query.workspaceId(),
                query.from() == null ? MIN_OCCURRED_AT : query.from(),
                query.to() == null ? MAX_OCCURRED_AT : query.to(),
                query.actorId(),
                cursor == null ? MAX_OCCURRED_AT : cursor.occurredAt(),
                cursorId,
                PageRequest.of(0, Math.max(limit, 1)));
        if (audits.isEmpty()) {
            return List.of();
        }

        List<UUID> transactionIds = audits.stream()
                .map(TransactionAuditTimelineProjection::getTransactionId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (transactionIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, TransactionRepository.ActivityTransactionContext> transactions = byId(
                transactionRepository.findActivityContextByWorkspaceIdAndIdIn(query.workspaceId(), transactionIds),
                TransactionRepository.ActivityTransactionContext::getId);
        Map<UUID, TransferDetailRepository.ActivityTransferContext> transfers = byId(
                transferDetailRepository.findActivityContextByWorkspaceIdAndTransactionIdIn(query.workspaceId(), transactionIds),
                TransferDetailRepository.ActivityTransferContext::getTransactionId);
        Map<UUID, ObligationOccurrenceRepository.ActivityObligationContext> obligations = byId(
                obligationOccurrenceRepository.findActivityContextByWorkspaceIdAndLinkedTransactionIdIn(query.workspaceId(), transactionIds),
                ObligationOccurrenceRepository.ActivityObligationContext::getLinkedTransactionId);
        Map<UUID, WalletBalanceSnapshotRepository.ActivityAdjustmentContext> adjustments = byId(
                walletBalanceSnapshotRepository.findActivityAdjustmentContextByWorkspaceIdAndTransactionIdIn(query.workspaceId(), transactionIds),
                WalletBalanceSnapshotRepository.ActivityAdjustmentContext::getAdjustmentTransactionId);

        return audits.stream()
                .map(audit -> mapper.map(
                        query.workspaceId(),
                        audit,
                        transactions.get(audit.getTransactionId()),
                        transfers.get(audit.getTransactionId()),
                        obligations.get(audit.getTransactionId()),
                        adjustments.get(audit.getTransactionId())))
                .flatMap(Optional::stream)
                .filter(candidate -> query.actions().isEmpty() || query.actions().contains(candidate.action()))
                .filter(candidate -> query.entityTypes().isEmpty() || query.entityTypes().contains(candidate.entityType()))
                .filter(candidate -> ActivityTimelineOrdering.isAfterCursor(candidate, query.cursor()))
                .sorted(ActivityCandidate.ORDERING)
                .limit(limit)
                .toList();
    }

    private <T> Map<UUID, T> byId(Collection<T> rows, Function<T, UUID> id) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        return rows.stream().collect(Collectors.toMap(id, Function.identity(), (left, ignored) -> left));
    }
}
