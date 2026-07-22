package com.moneyflowbackend.transaction.audit;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.transaction.audit.dto.TransactionAuditResponse;
import com.moneyflowbackend.transaction.model.Transaction;
import com.moneyflowbackend.transaction.model.TransferDetail;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.transaction.repository.TransferDetailRepository;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TransactionAuditService {
    private final TransactionAuditLogRepository auditLogRepository;
    private final TransactionRepository transactionRepository;
    private final TransferDetailRepository transferDetailRepository;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    public TransactionAuditService(
            TransactionAuditLogRepository auditLogRepository,
            TransactionRepository transactionRepository,
            TransferDetailRepository transferDetailRepository,
            UserRepository userRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository) {
        this.auditLogRepository = auditLogRepository;
        this.transactionRepository = transactionRepository;
        this.transferDetailRepository = transferDetailRepository;
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
    }

    @Transactional(readOnly = true)
    public List<TransactionAuditResponse> list(UUID workspaceId, UUID transactionId, UUID userId) {
        requireOwner(workspaceId, userId);
        transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)
                .orElseThrow(() -> new BusinessException("TRANSACTION_NOT_FOUND", "Transaction not found", HttpStatus.NOT_FOUND));
        return auditLogRepository.findByWorkspaceIdAndTransactionIdOrderByCreatedAtAsc(workspaceId, transactionId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TransactionAuditResponse> listByTransactionId(UUID transactionId, UUID userId) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new BusinessException("TRANSACTION_NOT_FOUND", "Transaction not found", HttpStatus.NOT_FOUND));
        return list(tx.getWorkspace().getId(), transactionId, userId);
    }

    public void record(Transaction tx, UUID actorUserId, TransactionAuditAction action,
                       Map<String, Object> before, Map<String, Object> after) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));
        auditLogRepository.save(TransactionAuditLog.builder()
                .workspace(tx.getWorkspace())
                .transaction(tx)
                .actorUser(actor)
                .action(action)
                .beforeData(before)
                .afterData(after)
                .build());
    }

    public Map<String, Object> snapshot(Transaction tx) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", tx.getTransactionType().name());
        data.put("adjustmentDirection", tx.getAdjustmentDirection() != null ? tx.getAdjustmentDirection().name() : null);
        data.put("status", tx.getTransactionStatus().name());
        data.put("amount", tx.getAmount());
        data.put("currency", tx.getCurrency());
        data.put("transactionDate", tx.getTransactionDate() != null ? tx.getTransactionDate().toString() : null);
        data.put("transactionTime", tx.getTransactionTime() != null ? tx.getTransactionTime().toString() : null);
        data.put("description", tx.getDescription());
        data.put("note", tx.getNote());
        data.put("sourceType", tx.getSourceType().name());
        data.put("rawInput", tx.getRawInput());
        data.put("walletId", tx.getWallet() != null ? tx.getWallet().getId() : null);
        data.put("walletName", tx.getWallet() != null ? tx.getWallet().getName() : null);
        data.put("categoryId", tx.getCategory() != null ? tx.getCategory().getId() : null);
        data.put("categoryName", tx.getCategory() != null ? tx.getCategory().getName() : null);
        data.put("spendingScope", tx.getSpendingScope() != null ? tx.getSpendingScope().name() : null);
        data.put("incomeSourceId", tx.getIncomeSource() != null ? tx.getIncomeSource().getId() : null);
        data.put("relatedIncomeSourceId", tx.getRelatedIncomeSource() != null ? tx.getRelatedIncomeSource().getId() : null);
        data.put("attributedPersonId", tx.getAttributedPerson() != null ? tx.getAttributedPerson().getId() : null);
        data.put("attributedPersonName", tx.getAttributedPerson() != null ? tx.getAttributedPerson().getDisplayName() : null);
        data.put("voiceRecordId", tx.getVoiceRecordId());
        data.put("deletedAt", tx.getDeletedAt() != null ? tx.getDeletedAt().toString() : null);
        if (tx.getTransactionType().name().equals("TRANSFER")) {
            transferDetailRepository.findById(tx.getId()).ifPresent(detail -> addTransfer(data, detail));
        }
        return data;
    }

    private void addTransfer(Map<String, Object> data, TransferDetail detail) {
        data.put("sourceWalletId", detail.getSourceWallet().getId());
        data.put("sourceWalletName", detail.getSourceWallet().getName());
        data.put("destinationWalletId", detail.getDestinationWallet().getId());
        data.put("destinationWalletName", detail.getDestinationWallet().getName());
    }

    private WorkspaceMember requireOwner(UUID workspaceId, UUID userId) {
        workspaceRepository.findById(workspaceId)
                .filter(workspace -> workspace.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException("WORKSPACE_NOT_FOUND", "Workspace not found", HttpStatus.NOT_FOUND));
        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserIdAndMemberStatus(workspaceId, userId, "ACTIVE")
                .orElseThrow(() -> new BusinessException("WORKSPACE_ACCESS_DENIED", "Workspace access denied", HttpStatus.FORBIDDEN));
        if (member.getRole() != WorkspaceRole.OWNER) {
            throw new BusinessException("FORBIDDEN", "Only workspace owner can view transaction audit", HttpStatus.FORBIDDEN);
        }
        return member;
    }

    private TransactionAuditResponse toResponse(TransactionAuditLog log) {
        User actor = log.getActorUser();
        return TransactionAuditResponse.builder()
                .id(log.getId())
                .transactionId(log.getTransaction() != null ? log.getTransaction().getId() : null)
                .action(log.getAction() == TransactionAuditAction.DELETE ? "SOFT_DELETE" : log.getAction().name())
                .actor(actor == null ? null : TransactionAuditResponse.Actor.builder()
                        .id(actor.getId())
                        .username(actor.getUsername())
                        .displayName(actor.getFullName())
                        .build())
                .before(log.getBeforeData())
                .after(log.getAfterData())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
