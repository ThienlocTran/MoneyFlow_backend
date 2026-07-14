package com.moneyflowbackend.transaction.repository;

import com.moneyflowbackend.transaction.model.Transaction;
import com.moneyflowbackend.transaction.model.TransactionSourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {
    long countByWorkspaceIdAndCategoryId(UUID workspaceId, UUID categoryId);
    boolean existsByWorkspaceIdAndVoiceRecordIdAndSourceType(UUID workspaceId, UUID voiceRecordId, TransactionSourceType sourceType);
    Optional<Transaction> findByIdAndWorkspaceId(UUID transactionId, UUID workspaceId);
    Optional<Transaction> findByWorkspaceIdAndMigrationKey(UUID workspaceId, String migrationKey);

    @Query("""
            SELECT t.wallet FROM Transaction t
            WHERE t.workspace.id = :workspaceId
              AND t.createdByUser.id = :userId
              AND t.transactionType = :transactionType
              AND t.wallet IS NOT NULL
              AND t.wallet.isActive = true
              AND t.deletedAt IS NULL
              AND t.affectsWalletBalance = true
              AND t.sourceType <> com.moneyflowbackend.transaction.model.TransactionSourceType.EXCEL_MIGRATION
            ORDER BY t.transactionDate DESC, t.createdAt DESC
            """)
    List<com.moneyflowbackend.wallet.model.Wallet> findRecentActiveWalletSuggestions(
            @Param("workspaceId") UUID workspaceId,
            @Param("userId") UUID userId,
            @Param("transactionType") com.moneyflowbackend.transaction.model.TransactionType transactionType);

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

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
            WHERE t.workspace.id = :workspaceId
              AND t.transactionType = :transactionType
              AND t.transactionStatus = com.moneyflowbackend.transaction.model.TransactionStatus.POSTED
              AND t.deletedAt IS NULL
              AND t.transactionDate >= :startDate
              AND t.transactionDate < :endDate
            """)
    BigDecimal sumPostedByTypeInMonth(
            @Param("workspaceId") UUID workspaceId,
            @Param("transactionType") com.moneyflowbackend.transaction.model.TransactionType transactionType,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
            JOIN t.category c
            WHERE t.workspace.id = :workspaceId
              AND c.jar.id = :jarId
              AND c.isActive = true
              AND c.isArchived = false
              AND t.transactionType = com.moneyflowbackend.transaction.model.TransactionType.EXPENSE
              AND t.transactionStatus = com.moneyflowbackend.transaction.model.TransactionStatus.POSTED
              AND t.deletedAt IS NULL
              AND t.transactionDate >= :startDate
              AND t.transactionDate < :endDate
            """)
    BigDecimal sumPostedExpenseByJarInMonth(
            @Param("workspaceId") UUID workspaceId,
            @Param("jarId") UUID jarId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("""
            SELECT COUNT(t) FROM Transaction t
            JOIN t.category c
            WHERE t.workspace.id = :workspaceId
              AND c.jar.id = :jarId
              AND c.isActive = true
              AND c.isArchived = false
              AND t.transactionType = com.moneyflowbackend.transaction.model.TransactionType.EXPENSE
              AND t.transactionStatus = com.moneyflowbackend.transaction.model.TransactionStatus.POSTED
              AND t.deletedAt IS NULL
              AND t.transactionDate >= :startDate
              AND t.transactionDate < :endDate
            """)
    long countPostedExpenseByJarInMonth(
            @Param("workspaceId") UUID workspaceId,
            @Param("jarId") UUID jarId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
