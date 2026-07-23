package com.moneyflowbackend.transaction.repository;

import com.moneyflowbackend.transaction.model.Transaction;
import com.moneyflowbackend.transaction.model.TransactionSourceType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {
    interface ActivityTransactionContext {
        UUID getId();
        com.moneyflowbackend.transaction.model.TransactionType getTransactionType();
        com.moneyflowbackend.transaction.model.TransactionStatus getTransactionStatus();
        com.moneyflowbackend.transaction.model.AdjustmentDirection getAdjustmentDirection();
        BigDecimal getAmount();
        LocalDate getBusinessDate();
        UUID getWalletId();
        UUID getCategoryId();
    }

    long countByWorkspaceIdAndCategoryId(UUID workspaceId, UUID categoryId);
    boolean existsByWorkspaceIdAndVoiceRecordIdAndSourceType(UUID workspaceId, UUID voiceRecordId, TransactionSourceType sourceType);
    Optional<Transaction> findByWorkspaceIdAndVoiceRecordIdAndSourceType(UUID workspaceId, UUID voiceRecordId, TransactionSourceType sourceType);
    List<Transaction> findAllByWorkspaceIdAndVoiceRecordIdAndSourceTypeOrderByCreatedAtAsc(
            UUID workspaceId,
            UUID voiceRecordId,
            TransactionSourceType sourceType);
    Optional<Transaction> findByIdAndWorkspaceId(UUID transactionId, UUID workspaceId);
    Optional<Transaction> findByWorkspaceIdAndMigrationKey(UUID workspaceId, String migrationKey);
    List<Transaction> findAllByWorkspaceIdAndSourceTypeAndSourceReferenceStartingWithOrderByCreatedAtAsc(
            UUID workspaceId,
            TransactionSourceType sourceType,
            String sourceReferencePrefix);

    @Query("""
            SELECT t.id AS id,
                   t.transactionType AS transactionType,
                   t.transactionStatus AS transactionStatus,
                   t.adjustmentDirection AS adjustmentDirection,
                   t.amount AS amount,
                   t.transactionDate AS businessDate,
                   w.id AS walletId,
                   c.id AS categoryId
            FROM Transaction t
            LEFT JOIN t.wallet w
            LEFT JOIN t.category c
            WHERE t.workspace.id = :workspaceId
              AND t.id IN :transactionIds
            """)
    List<ActivityTransactionContext> findActivityContextByWorkspaceIdAndIdIn(
            @Param("workspaceId") UUID workspaceId,
            @Param("transactionIds") Collection<UUID> transactionIds);

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
            SELECT t.category.id, COUNT(t) FROM Transaction t
            WHERE t.workspace.id = :workspaceId
              AND t.category.id IN :categoryIds
            GROUP BY t.category.id
            """)
    List<Object[]> countByWorkspaceIdAndCategoryIds(
            @Param("workspaceId") UUID workspaceId,
            @Param("categoryIds") Collection<UUID> categoryIds);

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

    @Query("""
            SELECT c.id, c.name, COUNT(t), COALESCE(SUM(t.amount), 0)
            FROM Transaction t
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
            GROUP BY c.id, c.name
            ORDER BY COALESCE(SUM(t.amount), 0) DESC, c.name ASC
            """)
    List<Object[]> sumPostedExpenseByJarCategoryInMonth(
            @Param("workspaceId") UUID workspaceId,
            @Param("jarId") UUID jarId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("""
            SELECT t.id, t.transactionDate, c.name, t.description, t.amount, w.name
            FROM Transaction t
            JOIN t.category c
            LEFT JOIN t.wallet w
            WHERE t.workspace.id = :workspaceId
              AND c.jar.id = :jarId
              AND c.isActive = true
              AND c.isArchived = false
              AND t.transactionType = com.moneyflowbackend.transaction.model.TransactionType.EXPENSE
              AND t.transactionStatus = com.moneyflowbackend.transaction.model.TransactionStatus.POSTED
              AND t.deletedAt IS NULL
              AND t.transactionDate >= :startDate
              AND t.transactionDate < :endDate
            ORDER BY t.transactionDate DESC, t.createdAt DESC
            """)
    List<Object[]> findRecentPostedExpenseByJarInMonth(
            @Param("workspaceId") UUID workspaceId,
            @Param("jarId") UUID jarId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    @Query("""
            SELECT COUNT(t) FROM Transaction t
            WHERE t.workspace.id = :workspaceId
              AND t.transactionType = com.moneyflowbackend.transaction.model.TransactionType.INCOME
              AND t.transactionStatus = com.moneyflowbackend.transaction.model.TransactionStatus.POSTED
              AND t.deletedAt IS NULL
              AND t.transactionDate >= :startDate
              AND t.transactionDate < :endDate
            """)
    long countPostedIncomeInMonth(
            @Param("workspaceId") UUID workspaceId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("""
            SELECT t.id, t.transactionDate, c.name, t.description, t.amount, w.name
            FROM Transaction t
            LEFT JOIN t.category c
            LEFT JOIN t.wallet w
            WHERE t.workspace.id = :workspaceId
              AND t.transactionType = com.moneyflowbackend.transaction.model.TransactionType.INCOME
              AND t.transactionStatus = com.moneyflowbackend.transaction.model.TransactionStatus.POSTED
              AND t.deletedAt IS NULL
              AND t.transactionDate >= :startDate
              AND t.transactionDate < :endDate
            ORDER BY t.transactionDate DESC, t.createdAt DESC
            """)
    List<Object[]> findRecentPostedIncomeInMonth(
            @Param("workspaceId") UUID workspaceId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);
}
