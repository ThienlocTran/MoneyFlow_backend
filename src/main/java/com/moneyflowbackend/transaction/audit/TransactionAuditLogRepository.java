package com.moneyflowbackend.transaction.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransactionAuditLogRepository extends JpaRepository<TransactionAuditLog, UUID> {
    List<TransactionAuditLog> findByWorkspaceIdAndTransactionIdOrderByCreatedAtAsc(UUID workspaceId, UUID transactionId);
}
