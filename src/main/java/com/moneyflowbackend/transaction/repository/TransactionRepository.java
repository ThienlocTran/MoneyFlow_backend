package com.moneyflowbackend.transaction.repository;

import com.moneyflowbackend.transaction.model.Transaction;
import com.moneyflowbackend.transaction.model.TransactionSourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {
    long countByWorkspaceIdAndCategoryId(UUID workspaceId, UUID categoryId);
    boolean existsByWorkspaceIdAndVoiceRecordIdAndSourceType(UUID workspaceId, UUID voiceRecordId, TransactionSourceType sourceType);
    Optional<Transaction> findByIdAndWorkspaceId(UUID transactionId, UUID workspaceId);
    Optional<Transaction> findByWorkspaceIdAndMigrationKey(UUID workspaceId, String migrationKey);

    @Query("""
            SELECT COUNT(t) FROM Transaction t
            LEFT JOIN TransferDetail td ON td.transaction.id = t.id
            WHERE t.workspace.id = :workspaceId
              AND (
                t.wallet.id = :walletId
                OR td.sourceWallet.id = :walletId
                OR td.destinationWallet.id = :walletId
              )
            """)
    long countWalletUsage(
            @Param("workspaceId") UUID workspaceId,
            @Param("walletId") UUID walletId);

    @Query("""
            SELECT COUNT(t) FROM Transaction t
            JOIN t.category c
            WHERE t.workspace.id = :workspaceId
              AND c.jar.id = :jarId
            """)
    long countJarUsage(
            @Param("workspaceId") UUID workspaceId,
            @Param("jarId") UUID jarId);
}
