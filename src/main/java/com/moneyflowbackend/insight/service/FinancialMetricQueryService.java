package com.moneyflowbackend.insight.service;

import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.insight.dto.FinancialPeriodMetric;
import com.moneyflowbackend.insight.dto.MetricBreakdownRow;
import com.moneyflowbackend.insight.dto.NoWalletIncomeMetric;
import com.moneyflowbackend.insight.dto.WalletAffectingMetric;
import com.moneyflowbackend.transaction.model.AdjustmentDirection;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class FinancialMetricQueryService {
    private static final String UNKNOWN_INCOME_SOURCE = "Chưa rõ nguồn";
    private static final String UNCATEGORIZED = "Chưa phân loại";
    private static final String NO_JAR = "Chưa có hũ";

    private final WorkspaceRepository workspaceRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public FinancialMetricQueryService(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    @Transactional(readOnly = true)
    public FinancialPeriodMetric getPeriodTotals(UUID workspaceId, LocalDate from, LocalDate to) {
        Workspace workspace = workspace(workspaceId);
        validateRange(from, to);
        Object[] row = (Object[]) single("""
                SELECT
                    COALESCE(SUM(CASE WHEN t.transactionType = :income THEN t.amount ELSE 0 END), 0),
                    COALESCE(SUM(CASE WHEN t.transactionType = :expense THEN t.amount ELSE 0 END), 0),
                    COUNT(t),
                    COALESCE(SUM(CASE WHEN t.transactionType = :income THEN 1 ELSE 0 END), 0),
                    COALESCE(SUM(CASE WHEN t.transactionType = :expense THEN 1 ELSE 0 END), 0)
                FROM Transaction t
                WHERE t.workspace.id = :workspaceId
                  AND t.transactionStatus = com.moneyflowbackend.transaction.model.TransactionStatus.POSTED
                  AND t.deletedAt IS NULL
                  AND t.transactionDate BETWEEN :from AND :to
                  AND t.transactionType IN (:income, :expense)
                """, workspaceId, from, to)
                .setParameter("income", TransactionType.INCOME)
                .setParameter("expense", TransactionType.EXPENSE)
                .getSingleResult();
        BigDecimal income = money(row[0]);
        BigDecimal expense = money(row[1]);
        return new FinancialPeriodMetric(
                workspaceId,
                from,
                to,
                workspace.getCurrency(),
                income,
                expense,
                income.subtract(expense),
                count(row[2]),
                count(row[3]),
                count(row[4]));
    }

    @Transactional(readOnly = true)
    public List<MetricBreakdownRow> getIncomeBySource(UUID workspaceId, LocalDate from, LocalDate to) {
        workspace(workspaceId);
        validateRange(from, to);
        BigDecimal total = getPeriodTotals(workspaceId, from, to).totalIncome();
        return rows(breakdown("""
                SELECT s.id, COALESCE(s.name, :fallback), COALESCE(SUM(t.amount), 0), COUNT(t)
                FROM Transaction t
                LEFT JOIN t.incomeSource s
                WHERE t.workspace.id = :workspaceId
                  AND t.transactionType = :type
                  AND t.transactionStatus = com.moneyflowbackend.transaction.model.TransactionStatus.POSTED
                  AND t.deletedAt IS NULL
                  AND t.transactionDate BETWEEN :from AND :to
                GROUP BY s.id, s.name
                ORDER BY COALESCE(SUM(t.amount), 0) DESC, COALESCE(s.name, :fallback) ASC
                """, workspaceId, from, to, UNKNOWN_INCOME_SOURCE)
                .setParameter("type", TransactionType.INCOME)
                .getResultList(), total);
    }

    @Transactional(readOnly = true)
    public NoWalletIncomeMetric getNoWalletIncome(UUID workspaceId, LocalDate from, LocalDate to) {
        workspace(workspaceId);
        validateRange(from, to);
        Object[] row = (Object[]) single("""
                SELECT COALESCE(SUM(t.amount), 0), COUNT(t)
                FROM Transaction t
                WHERE t.workspace.id = :workspaceId
                  AND t.transactionType = :type
                  AND t.wallet IS NULL
                  AND t.transactionStatus = com.moneyflowbackend.transaction.model.TransactionStatus.POSTED
                  AND t.deletedAt IS NULL
                  AND t.transactionDate BETWEEN :from AND :to
                """, workspaceId, from, to)
                .setParameter("type", TransactionType.INCOME)
                .getSingleResult();
        List<UUID> samples = entityManager.createQuery("""
                SELECT t.id
                FROM Transaction t
                WHERE t.workspace.id = :workspaceId
                  AND t.transactionType = :type
                  AND t.wallet IS NULL
                  AND t.transactionStatus = com.moneyflowbackend.transaction.model.TransactionStatus.POSTED
                  AND t.deletedAt IS NULL
                  AND t.transactionDate BETWEEN :from AND :to
                ORDER BY t.transactionDate DESC, t.createdAt DESC
                """, UUID.class)
                .setParameter("workspaceId", workspaceId)
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("type", TransactionType.INCOME)
                .setMaxResults(5)
                .getResultList();
        return new NoWalletIncomeMetric(money(row[0]), count(row[1]), samples);
    }

    @Transactional(readOnly = true)
    public List<MetricBreakdownRow> getExpenseByCategory(UUID workspaceId, LocalDate from, LocalDate to) {
        BigDecimal total = getPeriodTotals(workspaceId, from, to).totalExpense();
        return rows(breakdown("""
                SELECT c.id, COALESCE(c.name, :fallback), COALESCE(SUM(t.amount), 0), COUNT(t)
                FROM Transaction t
                LEFT JOIN t.category c
                WHERE t.workspace.id = :workspaceId
                  AND t.transactionType = :type
                  AND t.transactionStatus = com.moneyflowbackend.transaction.model.TransactionStatus.POSTED
                  AND t.deletedAt IS NULL
                  AND t.transactionDate BETWEEN :from AND :to
                GROUP BY c.id, c.name
                ORDER BY COALESCE(SUM(t.amount), 0) DESC, COALESCE(c.name, :fallback) ASC
                """, workspaceId, from, to, UNCATEGORIZED)
                .setParameter("type", TransactionType.EXPENSE)
                .getResultList(), total);
    }

    @Transactional(readOnly = true)
    public List<MetricBreakdownRow> getExpenseByJar(UUID workspaceId, LocalDate from, LocalDate to) {
        BigDecimal total = getPeriodTotals(workspaceId, from, to).totalExpense();
        return rows(breakdown("""
                SELECT j.id,
                       CASE WHEN c.id IS NULL THEN :uncategorized ELSE COALESCE(j.name, :fallback) END,
                       COALESCE(SUM(t.amount), 0),
                       COUNT(t)
                FROM Transaction t
                LEFT JOIN t.category c
                LEFT JOIN c.jar j
                WHERE t.workspace.id = :workspaceId
                  AND t.transactionType = :type
                  AND t.transactionStatus = com.moneyflowbackend.transaction.model.TransactionStatus.POSTED
                  AND t.deletedAt IS NULL
                  AND t.transactionDate BETWEEN :from AND :to
                GROUP BY j.id, CASE WHEN c.id IS NULL THEN :uncategorized ELSE COALESCE(j.name, :fallback) END
                ORDER BY COALESCE(SUM(t.amount), 0) DESC, CASE WHEN c.id IS NULL THEN :uncategorized ELSE COALESCE(j.name, :fallback) END ASC
                """, workspaceId, from, to, NO_JAR)
                .setParameter("type", TransactionType.EXPENSE)
                .setParameter("uncategorized", UNCATEGORIZED)
                .getResultList(), total);
    }

    @Transactional(readOnly = true)
    public MetricBreakdownRow getUncategorizedExpense(UUID workspaceId, LocalDate from, LocalDate to) {
        workspace(workspaceId);
        validateRange(from, to);
        Object[] row = (Object[]) single("""
                SELECT COALESCE(SUM(t.amount), 0), COUNT(t)
                FROM Transaction t
                WHERE t.workspace.id = :workspaceId
                  AND t.transactionType = :type
                  AND t.category IS NULL
                  AND t.transactionStatus = com.moneyflowbackend.transaction.model.TransactionStatus.POSTED
                  AND t.deletedAt IS NULL
                  AND t.transactionDate BETWEEN :from AND :to
                """, workspaceId, from, to)
                .setParameter("type", TransactionType.EXPENSE)
                .getSingleResult();
        BigDecimal total = getPeriodTotals(workspaceId, from, to).totalExpense();
        BigDecimal amount = money(row[0]);
        return new MetricBreakdownRow(null, UNCATEGORIZED, amount, count(row[1]), percent(amount, total));
    }

    @Transactional(readOnly = true)
    public WalletAffectingMetric getWalletAffectingSummary(UUID workspaceId, LocalDate from, LocalDate to) {
        workspace(workspaceId);
        validateRange(from, to);
        Object[] row = (Object[]) single("""
                SELECT
                    COALESCE(SUM(CASE WHEN t.transactionType IN :incomeTypes AND t.wallet IS NOT NULL AND t.affectsWalletBalance = true THEN t.amount ELSE 0 END), 0),
                    COALESCE(SUM(CASE WHEN t.transactionType IN :expenseTypes AND t.wallet IS NOT NULL AND t.affectsWalletBalance = true THEN t.amount ELSE 0 END), 0),
                    COALESCE(SUM(CASE WHEN t.transactionType = :income AND t.wallet IS NULL THEN t.amount ELSE 0 END), 0),
                    COALESCE(SUM(CASE WHEN t.transactionType = :adjustment AND t.affectsWalletBalance = true AND t.adjustmentDirection = :increase THEN t.amount WHEN t.transactionType = :adjustment AND t.affectsWalletBalance = true AND t.adjustmentDirection = :decrease THEN -t.amount ELSE 0 END), 0)
                FROM Transaction t
                WHERE t.workspace.id = :workspaceId
                  AND t.transactionStatus = com.moneyflowbackend.transaction.model.TransactionStatus.POSTED
                  AND t.deletedAt IS NULL
                  AND t.transactionDate BETWEEN :from AND :to
                """, workspaceId, from, to)
                .setParameter("incomeTypes", List.of(TransactionType.INCOME, TransactionType.LOAN_COLLECTION, TransactionType.BORROWING_RECEIPT))
                .setParameter("expenseTypes", List.of(TransactionType.EXPENSE, TransactionType.LOAN_DISBURSEMENT, TransactionType.BORROWING_REPAYMENT))
                .setParameter("income", TransactionType.INCOME)
                .setParameter("adjustment", TransactionType.ADJUSTMENT)
                .setParameter("increase", AdjustmentDirection.INCREASE)
                .setParameter("decrease", AdjustmentDirection.DECREASE)
                .getSingleResult();
        BigDecimal transferIn = transferTotal(workspaceId, from, to, true);
        BigDecimal transferOut = transferTotal(workspaceId, from, to, false);
        return new WalletAffectingMetric(money(row[0]), money(row[1]), money(row[2]), transferIn, transferOut, money(row[3]));
    }

    private Workspace workspace(UUID workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .filter(workspace -> workspace.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException("WORKSPACE_NOT_FOUND", "Workspace not found", HttpStatus.NOT_FOUND));
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new BusinessException("INVALID_INSIGHT_DATE_RANGE", "Insight date range is invalid");
        }
    }

    private jakarta.persistence.Query single(String jpql, UUID workspaceId, LocalDate from, LocalDate to) {
        return entityManager.createQuery(jpql)
                .setParameter("workspaceId", workspaceId)
                .setParameter("from", from)
                .setParameter("to", to);
    }

    private jakarta.persistence.Query breakdown(String jpql, UUID workspaceId, LocalDate from, LocalDate to, String fallback) {
        return single(jpql, workspaceId, from, to)
                .setParameter("fallback", fallback);
    }

    private BigDecimal transferTotal(UUID workspaceId, LocalDate from, LocalDate to, boolean incoming) {
        String walletSide = incoming ? "destinationWallet" : "sourceWallet";
        return money(entityManager.createQuery("""
                SELECT COALESCE(SUM(td.transaction.amount), 0)
                FROM TransferDetail td JOIN td.%s w
                WHERE w.workspace.id = :workspaceId
                  AND td.transaction.workspace.id = :workspaceId
                  AND td.transaction.transactionType = :type
                  AND td.transaction.transactionStatus = com.moneyflowbackend.transaction.model.TransactionStatus.POSTED
                  AND td.transaction.deletedAt IS NULL
                  AND td.transaction.affectsWalletBalance = true
                  AND td.transaction.transactionDate BETWEEN :from AND :to
                """.formatted(walletSide))
                .setParameter("workspaceId", workspaceId)
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("type", TransactionType.TRANSFER)
                .getSingleResult());
    }

    private List<MetricBreakdownRow> rows(List<Object[]> rows, BigDecimal total) {
        return rows.stream()
                .map(row -> {
                    BigDecimal amount = money(row[2]);
                    return new MetricBreakdownRow((UUID) row[0], (String) row[1], amount, count(row[3]), percent(amount, total));
                })
                .toList();
    }

    private BigDecimal percent(BigDecimal amount, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal money(Object value) {
        return value == null ? BigDecimal.ZERO : (BigDecimal) value;
    }

    private long count(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }
}
