package com.moneyflowbackend.transaction.repository;

import com.moneyflowbackend.transaction.model.Transaction;
import com.moneyflowbackend.transaction.model.TransactionSourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {
    long countByWorkspaceIdAndCategoryId(UUID workspaceId, UUID categoryId);
    boolean existsByWorkspaceIdAndVoiceRecordIdAndSourceType(UUID workspaceId, UUID voiceRecordId, TransactionSourceType sourceType);
    Optional<Transaction> findByIdAndWorkspaceId(UUID transactionId, UUID workspaceId);
    Optional<Transaction> findByWorkspaceIdAndMigrationKey(UUID workspaceId, String migrationKey);
}
