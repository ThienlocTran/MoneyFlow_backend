package com.moneyflowbackend.insight.service;

import com.moneyflowbackend.insight.dto.FinancialActionMetric;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class FinancialActionItemQueryService {
    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public FinancialActionMetric missingCategoryExpense(UUID workspaceId, LocalDate from, LocalDate to) {
        return metric("""
                SELECT COALESCE(SUM(t.amount), 0), COUNT(t)
                FROM Transaction t
                WHERE t.workspace.id = :workspaceId
                  AND t.transactionType = com.moneyflowbackend.transaction.model.TransactionType.EXPENSE
                  AND t.transactionStatus = com.moneyflowbackend.transaction.model.TransactionStatus.POSTED
                  AND t.deletedAt IS NULL
                  AND t.category IS NULL
                  AND t.transactionDate BETWEEN :from AND :to
                """, workspaceId, from, to);
    }

    @Transactional(readOnly = true)
    public FinancialActionMetric missingWalletExpense(UUID workspaceId, LocalDate from, LocalDate to) {
        return metric("""
                SELECT COALESCE(SUM(t.amount), 0), COUNT(t)
                FROM Transaction t
                WHERE t.workspace.id = :workspaceId
                  AND t.transactionType = com.moneyflowbackend.transaction.model.TransactionType.EXPENSE
                  AND t.transactionStatus = com.moneyflowbackend.transaction.model.TransactionStatus.POSTED
                  AND t.deletedAt IS NULL
                  AND t.wallet IS NULL
                  AND t.transactionDate BETWEEN :from AND :to
                """, workspaceId, from, to);
    }

    @Transactional(readOnly = true)
    public FinancialActionMetric historicalAnalyticsOnly(UUID workspaceId, LocalDate from, LocalDate to) {
        return metric("""
                SELECT COALESCE(SUM(t.amount), 0), COUNT(t)
                FROM Transaction t
                WHERE t.workspace.id = :workspaceId
                  AND t.transactionStatus = com.moneyflowbackend.transaction.model.TransactionStatus.POSTED
                  AND t.deletedAt IS NULL
                  AND t.historical = true
                  AND t.affectsWalletBalance = false
                  AND t.transactionDate BETWEEN :from AND :to
                """, workspaceId, from, to);
    }

    @Transactional(readOnly = true)
    public long pendingVoiceDrafts(UUID workspaceId) {
        return count("""
                SELECT COUNT(d)
                FROM VoiceSessionDraft d
                WHERE d.voiceSession.workspace.id = :workspaceId
                  AND d.status IN (
                    com.moneyflowbackend.voice.session.VoiceSessionDraftStatus.DRAFT,
                    com.moneyflowbackend.voice.session.VoiceSessionDraftStatus.NEEDS_REVIEW,
                    com.moneyflowbackend.voice.session.VoiceSessionDraftStatus.READY
                  )
                """, workspaceId);
    }

    @Transactional(readOnly = true)
    public long pendingReceiptDrafts(UUID workspaceId) {
        return count("""
                SELECT COUNT(d)
                FROM ReceiptSessionDraft d
                WHERE d.workspaceId = :workspaceId
                  AND d.status IN (
                    com.moneyflowbackend.receipt.session.ReceiptSessionDraftStatus.DRAFT,
                    com.moneyflowbackend.receipt.session.ReceiptSessionDraftStatus.NEEDS_REVIEW
                  )
                """, workspaceId);
    }

    @Transactional(readOnly = true)
    public long ocrReviewRequired(UUID workspaceId) {
        return count("""
                SELECT COUNT(d)
                FROM ReceiptSessionDraft d
                WHERE d.workspaceId = :workspaceId
                  AND d.status IN (
                    com.moneyflowbackend.receipt.session.ReceiptSessionDraftStatus.DRAFT,
                    com.moneyflowbackend.receipt.session.ReceiptSessionDraftStatus.NEEDS_REVIEW
                  )
                  AND (d.amount IS NULL OR d.transactionDate IS NULL OR d.walletId IS NULL OR d.categoryId IS NULL OR d.confidence IS NULL OR d.confidence < 0.70)
                """, workspaceId);
    }

    @Transactional(readOnly = true)
    public long payableDebtsMissingDueDate(UUID workspaceId) {
        return count("""
                SELECT COUNT(d)
                FROM Debt d
                WHERE d.workspaceId = :workspaceId
                  AND d.direction = 'PAYABLE'
                  AND d.debtStatus IN ('OPEN', 'PARTIAL')
                  AND d.dueOn IS NULL
                """, workspaceId);
    }

    private FinancialActionMetric metric(String jpql, UUID workspaceId, LocalDate from, LocalDate to) {
        Object[] row = (Object[]) entityManager.createQuery(jpql)
                .setParameter("workspaceId", workspaceId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult();
        return new FinancialActionMetric(money(row[0]), ((Number) row[1]).longValue());
    }

    private long count(String jpql, UUID workspaceId) {
        return ((Number) entityManager.createQuery(jpql)
                .setParameter("workspaceId", workspaceId)
                .getSingleResult()).longValue();
    }

    private BigDecimal money(Object value) {
        return value == null ? BigDecimal.ZERO : (BigDecimal) value;
    }
}
