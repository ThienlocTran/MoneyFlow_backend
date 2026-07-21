package com.moneyflowbackend.activity.source;

import com.moneyflowbackend.activity.domain.ActivityAction;
import com.moneyflowbackend.activity.domain.ActivityEntityType;
import com.moneyflowbackend.activity.domain.ActivitySource;
import com.moneyflowbackend.activity.domain.NavigationTargetType;
import com.moneyflowbackend.activity.dto.ActivityActorSummary;
import com.moneyflowbackend.activity.dto.ActivityNavigationTarget;
import com.moneyflowbackend.activity.internal.ActivityCandidate;
import com.moneyflowbackend.activity.internal.ActivitySafeDetails;
import com.moneyflowbackend.activity.query.ActivityIdFactory;
import com.moneyflowbackend.obligation.repository.ObligationOccurrenceRepository;
import com.moneyflowbackend.transaction.audit.TransactionAuditAction;
import com.moneyflowbackend.transaction.audit.TransactionAuditTimelineProjection;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.transaction.repository.TransferDetailRepository;
import com.moneyflowbackend.wallet.repository.WalletBalanceSnapshotRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
class TransactionActivityMapper {
    Optional<ActivityCandidate> map(
            UUID workspaceId,
            TransactionAuditTimelineProjection audit,
            TransactionRepository.ActivityTransactionContext transaction,
            TransferDetailRepository.ActivityTransferContext transfer,
            ObligationOccurrenceRepository.ActivityObligationContext obligation,
            WalletBalanceSnapshotRepository.ActivityAdjustmentContext adjustment) {
        if (transaction == null || audit.getAuditAction() == TransactionAuditAction.IMPORT) {
            return Optional.empty();
        }
        if (adjustment != null && adjustment.getDailyClosingId() != null) {
            return Optional.empty();
        }
        ActivityAction action = action(audit.getAuditAction(), transaction, obligation);
        if (action == null) {
            return Optional.empty();
        }
        ActivityEntityType entityType = entityType(action);
        UUID entityId = entityId(action, transaction, obligation);
        if (entityId == null) {
            return Optional.empty();
        }

        return Optional.of(new ActivityCandidate(
                ActivityIdFactory.stableId(ActivitySource.TRANSACTION_AUDIT, audit.getId()),
                workspaceId,
                audit.getOccurredAt(),
                ActivitySource.TRANSACTION_AUDIT.rank(),
                ActivitySource.TRANSACTION_AUDIT,
                actor(audit),
                action,
                entityType,
                entityId,
                action == ActivityAction.OBLIGATION_CONFIRMED && obligation.getActualAmount() != null
                        ? obligation.getActualAmount()
                        : transaction.getAmount(),
                direction(action, transaction, obligation),
                transaction.getBusinessDate(),
                navigation(action, transaction),
                details(action, transaction, transfer, obligation, adjustment)));
    }

    private ActivityAction action(
            TransactionAuditAction auditAction,
            TransactionRepository.ActivityTransactionContext transaction,
            ObligationOccurrenceRepository.ActivityObligationContext obligation) {
        return switch (auditAction) {
            case CREATE -> createAction(transaction, obligation);
            case UPDATE -> ActivityAction.TRANSACTION_UPDATED;
            case DELETE -> ActivityAction.TRANSACTION_VOIDED;
            case RESTORE -> ActivityAction.TRANSACTION_RESTORED;
            case IMPORT -> null;
        };
    }

    private ActivityAction createAction(
            TransactionRepository.ActivityTransactionContext transaction,
            ObligationOccurrenceRepository.ActivityObligationContext obligation) {
        if (obligation != null) {
            return ActivityAction.OBLIGATION_CONFIRMED;
        }
        if (transaction.getTransactionType() == TransactionType.TRANSFER) {
            return ActivityAction.TRANSFER_CREATED;
        }
        if (transaction.getTransactionType() == TransactionType.ADJUSTMENT) {
            return ActivityAction.ADJUSTMENT_CREATED;
        }
        if (transaction.getTransactionType() == TransactionType.INCOME
                || transaction.getTransactionType() == TransactionType.EXPENSE) {
            return ActivityAction.TRANSACTION_CREATED;
        }
        return null;
    }

    private ActivityEntityType entityType(ActivityAction action) {
        return switch (action) {
            case TRANSFER_CREATED -> ActivityEntityType.TRANSFER;
            case OBLIGATION_CONFIRMED -> ActivityEntityType.OBLIGATION_OCCURRENCE;
            default -> ActivityEntityType.TRANSACTION;
        };
    }

    private UUID entityId(
            ActivityAction action,
            TransactionRepository.ActivityTransactionContext transaction,
            ObligationOccurrenceRepository.ActivityObligationContext obligation) {
        return action == ActivityAction.OBLIGATION_CONFIRMED ? obligation.getOccurrenceId() : transaction.getId();
    }

    private ActivityActorSummary actor(TransactionAuditTimelineProjection audit) {
        return audit.getActorUserId() == null
                ? ActivityActorSummary.unknown()
                : ActivityActorSummary.user(audit.getActorUserId(), audit.getActorDisplayName());
    }

    private String direction(
            ActivityAction action,
            TransactionRepository.ActivityTransactionContext transaction,
            ObligationOccurrenceRepository.ActivityObligationContext obligation) {
        if (action == ActivityAction.OBLIGATION_CONFIRMED) {
            return obligation.getDirection().name();
        }
        return transaction.getTransactionType().name();
    }

    private ActivityNavigationTarget navigation(
            ActivityAction action,
            TransactionRepository.ActivityTransactionContext transaction) {
        if (action == ActivityAction.OBLIGATION_CONFIRMED) {
            return new ActivityNavigationTarget(NavigationTargetType.FINANCIAL_INBOX, null);
        }
        return new ActivityNavigationTarget(NavigationTargetType.TRANSACTION, transaction.getId());
    }

    private Map<String, Object> details(
            ActivityAction action,
            TransactionRepository.ActivityTransactionContext transaction,
            TransferDetailRepository.ActivityTransferContext transfer,
            ObligationOccurrenceRepository.ActivityObligationContext obligation,
            WalletBalanceSnapshotRepository.ActivityAdjustmentContext adjustment) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("transactionType", transaction.getTransactionType().name());
        details.put("transactionStatus", transaction.getTransactionStatus().name());
        details.put("walletId", transaction.getWalletId());
        details.put("categoryId", transaction.getCategoryId());
        if (action == ActivityAction.TRANSFER_CREATED && transfer != null) {
            details.put("sourceWalletId", transfer.getSourceWalletId());
            details.put("destinationWalletId", transfer.getDestinationWalletId());
        }
        if (action == ActivityAction.ADJUSTMENT_CREATED) {
            details.put("adjustmentDirection", transaction.getAdjustmentDirection() == null ? null : transaction.getAdjustmentDirection().name());
            details.put("dailyClosingId", adjustment == null ? null : adjustment.getDailyClosingId());
        }
        if (action == ActivityAction.OBLIGATION_CONFIRMED) {
            details.put("obligationOccurrenceId", obligation.getOccurrenceId());
            details.put("obligationTemplateId", obligation.getTemplateId());
            details.put("linkedTransactionId", obligation.getLinkedTransactionId());
        }
        return ActivitySafeDetails.whitelist(details);
    }
}
