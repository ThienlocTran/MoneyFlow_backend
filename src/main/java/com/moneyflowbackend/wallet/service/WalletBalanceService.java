package com.moneyflowbackend.wallet.service;

import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.transaction.model.AdjustmentDirection;
import com.moneyflowbackend.transaction.model.TransactionStatus;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.repository.WalletRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WalletBalanceService {
    private final WalletRepository walletRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public WalletBalanceService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateCurrentBalance(UUID walletId) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new BusinessException("WALLET_NOT_FOUND", "Không tìm thấy ví"));
        return calculateBalance(wallet, null);
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateBalanceAtEndOfDay(UUID walletId, LocalDate closingDate) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new BusinessException("WALLET_NOT_FOUND", "Không tìm thấy ví"));
        return calculateBalance(wallet, requireClosingDate(closingDate));
    }

    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> calculateCurrentBalances(Collection<Wallet> wallets) {
        return calculateBalances(wallets, null);
    }

    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> calculateBalancesAtEndOfDay(Collection<Wallet> wallets, LocalDate closingDate) {
        return calculateBalances(wallets, requireClosingDate(closingDate));
    }

    private BigDecimal calculateBalance(Wallet wallet, LocalDate closingDate) {
        return calculateBalances(List.of(wallet), closingDate)
                .getOrDefault(wallet.getId(), wallet.getOpeningBalance());
    }

    private Map<UUID, BigDecimal> calculateBalances(Collection<Wallet> wallets, LocalDate closingDate) {
        Map<UUID, BigDecimal> balances = new HashMap<>();
        List<UUID> walletIds = new ArrayList<>(wallets.size());
        for (Wallet wallet : wallets) {
            balances.put(wallet.getId(), wallet.getOpeningBalance());
            walletIds.add(wallet.getId());
        }
        if (walletIds.isEmpty()) {
            return balances;
        }

        applyDeltas(balances, walletTransactionDeltas(walletIds, closingDate));
        applyDeltas(balances, transferDeltas(walletIds, closingDate, true));
        applyDeltas(balances, transferDeltas(walletIds, closingDate, false));
        return balances;
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> walletTransactionDeltas(List<UUID> walletIds, LocalDate closingDate) {
        String cutoffFilter = closingDate == null ? "" : "AND t.transactionDate <= :closingDate ";
        var query = entityManager.createQuery(
                "SELECT w.id, COALESCE(SUM(CASE " +
                "WHEN t.transactionType IN :incomeTypes THEN t.amount " +
                "WHEN t.transactionType IN :expenseTypes THEN -t.amount " +
                "WHEN t.transactionType = :adjustmentType AND t.adjustmentDirection = :increase THEN t.amount " +
                "WHEN t.transactionType = :adjustmentType AND t.adjustmentDirection = :decrease THEN -t.amount " +
                "ELSE 0 END), 0) " +
                "FROM Transaction t JOIN t.wallet w " +
                "WHERE w.id IN :walletIds " +
                "AND t.workspace.id = w.workspace.id " +
                "AND t.transactionStatus = :status " +
                "AND t.deletedAt IS NULL " +
                "AND t.affectsWalletBalance = true " +
                "AND t.transactionType IN :types " +
                "AND (w.openingDate IS NULL OR t.transactionDate >= w.openingDate) " +
                cutoffFilter +
                "GROUP BY w.id")
                .setParameter("walletIds", walletIds)
                .setParameter("status", TransactionStatus.POSTED)
                .setParameter("incomeTypes", incomeBalanceTypes())
                .setParameter("expenseTypes", expenseBalanceTypes())
                .setParameter("adjustmentType", TransactionType.ADJUSTMENT)
                .setParameter("increase", AdjustmentDirection.INCREASE)
                .setParameter("decrease", AdjustmentDirection.DECREASE)
                .setParameter("types", balanceTransactionTypes());
        if (closingDate != null) {
            query.setParameter("closingDate", closingDate);
        }
        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> transferDeltas(List<UUID> walletIds, LocalDate closingDate, boolean incoming) {
        String walletSide = incoming ? "destinationWallet" : "sourceWallet";
        String sign = incoming ? "td.transaction.amount" : "-td.transaction.amount";
        String cutoffFilter = closingDate == null ? "" : "AND td.transaction.transactionDate <= :closingDate ";
        var query = entityManager.createQuery(
                "SELECT w.id, COALESCE(SUM(" + sign + "), 0) " +
                "FROM TransferDetail td JOIN td." + walletSide + " w " +
                "WHERE w.id IN :walletIds " +
                "AND td.transaction.workspace.id = w.workspace.id " +
                "AND td.transaction.transactionType = :type " +
                "AND td.transaction.transactionStatus = :status " +
                "AND td.transaction.deletedAt IS NULL " +
                "AND td.transaction.affectsWalletBalance = true " +
                "AND (w.openingDate IS NULL OR td.transaction.transactionDate >= w.openingDate) " +
                cutoffFilter +
                "GROUP BY w.id")
                .setParameter("walletIds", walletIds)
                .setParameter("type", TransactionType.TRANSFER)
                .setParameter("status", TransactionStatus.POSTED);
        if (closingDate != null) {
            query.setParameter("closingDate", closingDate);
        }
        return query.getResultList();
    }

    private void applyDeltas(Map<UUID, BigDecimal> balances, List<Object[]> rows) {
        for (Object[] row : rows) {
            UUID walletId = (UUID) row[0];
            BigDecimal delta = (BigDecimal) row[1];
            balances.computeIfPresent(walletId, (id, balance) -> balance.add(delta));
        }
    }

    private List<TransactionType> incomeBalanceTypes() {
        return List.of(
                TransactionType.INCOME,
                TransactionType.LOAN_COLLECTION,
                TransactionType.BORROWING_RECEIPT);
    }

    private List<TransactionType> expenseBalanceTypes() {
        return List.of(
                TransactionType.EXPENSE,
                TransactionType.LOAN_DISBURSEMENT,
                TransactionType.BORROWING_REPAYMENT);
    }

    private List<TransactionType> balanceTransactionTypes() {
        return List.of(
                TransactionType.INCOME,
                TransactionType.LOAN_COLLECTION,
                TransactionType.BORROWING_RECEIPT,
                TransactionType.EXPENSE,
                TransactionType.LOAN_DISBURSEMENT,
                TransactionType.BORROWING_REPAYMENT,
                TransactionType.ADJUSTMENT);
    }

    private LocalDate requireClosingDate(LocalDate closingDate) {
        if (closingDate == null) {
            throw new IllegalArgumentException("closingDate is required");
        }
        return closingDate;
    }
}
