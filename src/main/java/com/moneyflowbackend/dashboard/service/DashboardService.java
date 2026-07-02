package com.moneyflowbackend.dashboard.service;

import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.dashboard.dto.*;
import com.moneyflowbackend.transaction.model.TransactionStatus;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.service.WalletService;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import com.moneyflowbackend.workspace.service.WorkspaceService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DashboardService {
    private static final String FALLBACK_ZONE = "Asia/Ho_Chi_Minh";

    private final WorkspaceService workspaceService;
    private final WorkspaceRepository workspaceRepository;
    private final WalletService walletService;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    public DashboardService(WorkspaceService workspaceService, WorkspaceRepository workspaceRepository, WalletService walletService, Clock clock) {
        this.workspaceService = workspaceService;
        this.workspaceRepository = workspaceRepository;
        this.walletService = walletService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(UUID workspaceId, String month, String comparisonMode, UUID userId) {
        workspaceService.verifyMembership(workspaceId, userId);
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .filter(ws -> ws.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException("WORKSPACE_NOT_FOUND", "Workspace not found", HttpStatus.NOT_FOUND));

        ComparisonMode mode = parseMode(comparisonMode);
        YearMonth selectedMonth = parseMonth(month, workspace);
        Period current = currentPeriod(selectedMonth, mode, workspace);
        Period previous = previousPeriod(current, mode);

        Totals currentTotals = totals(workspaceId, current.from(), current.to());
        Totals previousTotals = totals(workspaceId, previous.from(), previous.to());
        List<DashboardCategoryResponse> expenseByCategory = categoryBreakdown(workspaceId, current.from(), current.to(), TransactionType.EXPENSE, currentTotals.expense());
        List<DashboardJarResponse> expenseByJar = jarBreakdown(workspaceId, current.from(), current.to(), currentTotals.expense());

        return DashboardResponse.builder()
                .period(DashboardPeriodResponse.builder()
                        .month(selectedMonth.toString())
                        .dateFrom(current.from())
                        .dateTo(current.to())
                        .startDate(current.from())
                        .endDate(current.to())
                        .comparisonMode(mode.name())
                        .build())
                .summary(DashboardSummaryBlockResponse.builder()
                        .income(currentTotals.income())
                        .expense(currentTotals.expense())
                        .net(currentTotals.net())
                        .transactionCount(currentTotals.count())
                        .build())
                .walletTotal(walletTotal(workspaceId))
                .income(currentTotals.income())
                .expense(currentTotals.expense())
                .netCashFlow(currentTotals.net())
                .transactionCount(currentTotals.count())
                .comparison(comparison(currentTotals, previousTotals, previous))
                .categoryBreakdown(expenseByCategory)
                .jarBreakdown(expenseByJar)
                .expenseByCategory(expenseByCategory)
                .expenseByJar(expenseByJar)
                .incomeByCategory(categoryBreakdown(workspaceId, current.from(), current.to(), TransactionType.INCOME, currentTotals.income()))
                .topIncreases(topCategoryChanges(workspaceId, current, previous, true))
                .topDecreases(topCategoryChanges(workspaceId, current, previous, false))
                .walletBalances(walletBalances(workspaceId, userId))
                .recentTransactions(recentTransactions(workspaceId, current.from(), current.to()))
                .build();
    }

    public DashboardMonthlyResponse getMonthlySummary(UUID workspaceId, int year, int month, UUID userId) {
        DashboardResponse dashboard = getDashboard(workspaceId, YearMonth.of(year, month).toString(), ComparisonMode.FULL_MONTH.name(), userId);
        BigDecimal income = dashboard.getIncome();
        BigDecimal net = dashboard.getNetCashFlow();
        double savingRate = income.compareTo(BigDecimal.ZERO) > 0
                ? net.divide(income, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;
        return DashboardMonthlyResponse.builder()
                .year(year)
                .month(month)
                .totalIncome(income)
                .totalExpense(dashboard.getExpense())
                .netCashFlow(net)
                .savingRate(savingRate)
                .transactionCount(dashboard.getTransactionCount())
                .build();
    }

    public List<DashboardCategoryResponse> getCategoryBreakdown(UUID workspaceId, int year, int month, UUID userId) {
        return getDashboard(workspaceId, YearMonth.of(year, month).toString(), ComparisonMode.FULL_MONTH.name(), userId).getExpenseByCategory();
    }

    public List<DashboardJarResponse> getJarBreakdown(UUID workspaceId, int year, int month, UUID userId) {
        return getDashboard(workspaceId, YearMonth.of(year, month).toString(), ComparisonMode.FULL_MONTH.name(), userId).getExpenseByJar();
    }

    public DashboardComparisonResponse getExpenseComparison(UUID workspaceId, int year, int month, UUID userId) {
        DashboardResponse dashboard = getDashboard(workspaceId, YearMonth.of(year, month).toString(), ComparisonMode.SAME_PERIOD.name(), userId);
        return dashboard.getComparison();
    }

    private Totals totals(UUID workspaceId, LocalDate from, LocalDate to) {
        BigDecimal income = sumByType(workspaceId, from, to, TransactionType.INCOME);
        BigDecimal expense = sumByType(workspaceId, from, to, TransactionType.EXPENSE);
        Long count = entityManager.createQuery(
                "SELECT COUNT(t) FROM Transaction t " +
                        "WHERE t.workspace.id = :workspaceId " + postedFilter() +
                        "AND t.transactionDate BETWEEN :from AND :to " +
                        "AND t.transactionType IN (:income, :expense)", Long.class)
                .setParameter("workspaceId", workspaceId)
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("income", TransactionType.INCOME)
                .setParameter("expense", TransactionType.EXPENSE)
                .getSingleResult();
        return new Totals(income, expense, income.subtract(expense), count);
    }

    private BigDecimal sumByType(UUID workspaceId, LocalDate from, LocalDate to, TransactionType type) {
        return entityManager.createQuery(
                "SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
                        "WHERE t.workspace.id = :workspaceId " + postedFilter() +
                        "AND t.transactionDate BETWEEN :from AND :to " +
                        "AND t.transactionType = :type", BigDecimal.class)
                .setParameter("workspaceId", workspaceId)
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("type", type)
                .getSingleResult();
    }

    private BigDecimal walletTotal(UUID workspaceId) {
        List<Wallet> wallets = entityManager.createQuery(
                "SELECT w FROM Wallet w WHERE w.workspace.id = :workspaceId AND w.isActive = true AND w.includeInTotal = true", Wallet.class)
                .setParameter("workspaceId", workspaceId)
                .getResultList();
        return wallets.stream().map(this::walletBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal walletBalance(Wallet wallet) {
        UUID walletId = wallet.getId();
        LocalDate openingDate = wallet.getOpeningDate();
        BigDecimal income = sumWallet(walletId, openingDate, List.of(TransactionType.INCOME, TransactionType.LOAN_COLLECTION, TransactionType.BORROWING_RECEIPT));
        BigDecimal expense = sumWallet(walletId, openingDate, List.of(TransactionType.EXPENSE, TransactionType.LOAN_DISBURSEMENT, TransactionType.BORROWING_REPAYMENT));
        BigDecimal transferTo = sumTransfer(walletId, openingDate, true);
        BigDecimal transferFrom = sumTransfer(walletId, openingDate, false);
        return wallet.getOpeningBalance().add(income).subtract(expense).add(transferTo).subtract(transferFrom);
    }

    private BigDecimal sumWallet(UUID walletId, LocalDate openingDate, List<TransactionType> types) {
        String dateFilter = openingDate == null ? "" : "AND t.transactionDate >= :openingDate ";
        var query = entityManager.createQuery(
                "SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
                        "WHERE t.wallet.id = :walletId " + ledgerFilter() +
                        "AND t.transactionType IN :types " + dateFilter, BigDecimal.class)
                .setParameter("walletId", walletId)
                .setParameter("types", types);
        if (openingDate != null) query.setParameter("openingDate", openingDate);
        return query.getSingleResult();
    }

    private BigDecimal sumTransfer(UUID walletId, LocalDate openingDate, boolean incoming) {
        String walletSide = incoming ? "destinationWallet" : "sourceWallet";
        String dateFilter = openingDate == null ? "" : "AND td.transaction.transactionDate >= :openingDate ";
        var query = entityManager.createQuery(
                "SELECT COALESCE(SUM(td.transaction.amount), 0) FROM TransferDetail td " +
                "WHERE td." + walletSide + ".id = :walletId " +
                "AND td.transaction.transactionType = :type " +
                "AND td.transaction.transactionStatus = :status " +
                "AND td.transaction.deletedAt IS NULL " +
                "AND td.transaction.affectsWalletBalance = true " + dateFilter, BigDecimal.class)
                .setParameter("walletId", walletId)
                .setParameter("type", TransactionType.TRANSFER)
                .setParameter("status", TransactionStatus.POSTED);
        if (openingDate != null) query.setParameter("openingDate", openingDate);
        return query.getSingleResult();
    }

    @SuppressWarnings("unchecked")
    private List<DashboardCategoryResponse> categoryBreakdown(UUID workspaceId, LocalDate from, LocalDate to, TransactionType type, BigDecimal total) {
        List<Object[]> rows = entityManager.createQuery(
                "SELECT c.id, c.name, j.id, j.name, c.categoryType, SUM(t.amount), COUNT(t) " +
                        "FROM Transaction t JOIN t.category c LEFT JOIN c.jar j " +
                        "WHERE t.workspace.id = :workspaceId " + postedFilter() +
                        "AND t.transactionDate BETWEEN :from AND :to " +
                        "AND t.transactionType = :type " +
                        "GROUP BY c.id, c.name, j.id, j.name, c.categoryType " +
                        "ORDER BY SUM(t.amount) DESC")
                .setParameter("workspaceId", workspaceId)
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("type", type)
                .getResultList();
        return rows.stream().map(row -> {
            BigDecimal amount = (BigDecimal) row[5];
            return DashboardCategoryResponse.builder()
                    .categoryId((UUID) row[0])
                    .categoryName((String) row[1])
                    .jarId((UUID) row[2])
                    .jarName((String) row[3])
                    .categoryType(((CategoryType) row[4]).name())
                    .amount(amount)
                    .totalAmount(amount)
                    .percentage(percent(amount, total))
                    .transactionCount((Long) row[6])
                    .build();
        }).toList();
    }

    @SuppressWarnings("unchecked")
    private List<DashboardJarResponse> jarBreakdown(UUID workspaceId, LocalDate from, LocalDate to, BigDecimal totalExpense) {
        List<Object[]> rows = entityManager.createQuery(
                "SELECT j.id, j.code, j.name, j.allocationPercent, SUM(t.amount) " +
                        "FROM Transaction t JOIN t.category c JOIN c.jar j " +
                        "WHERE t.workspace.id = :workspaceId " + postedFilter() +
                        "AND t.transactionDate BETWEEN :from AND :to " +
                        "AND t.transactionType = :type " +
                        "GROUP BY j.id, j.code, j.name, j.allocationPercent " +
                        "ORDER BY SUM(t.amount) DESC")
                .setParameter("workspaceId", workspaceId)
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("type", TransactionType.EXPENSE)
                .getResultList();
        return rows.stream().map(row -> {
            BigDecimal amount = (BigDecimal) row[4];
            return DashboardJarResponse.builder()
                    .jarId((UUID) row[0])
                    .jarCode((String) row[1])
                    .jarName((String) row[2])
                    .allocationPercent((BigDecimal) row[3])
                    .amount(amount)
                    .totalAmount(amount)
                    .percentage(percent(amount, totalExpense))
                    .usagePercent(null)
                    .build();
        }).toList();
    }

    @SuppressWarnings("unchecked")
    private List<DashboardRecentTransactionResponse> recentTransactions(UUID workspaceId, LocalDate from, LocalDate to) {
        List<Object[]> rows = entityManager.createQuery(
                "SELECT t.id, t.transactionType, t.transactionStatus, t.amount, t.transactionDate, t.transactionTime, " +
                        "t.description, w.id, w.name, c.id, c.name, sw.name, dw.name " +
                        "FROM Transaction t LEFT JOIN t.wallet w LEFT JOIN t.category c " +
                        "LEFT JOIN TransferDetail td ON td.transaction = t " +
                        "LEFT JOIN td.sourceWallet sw LEFT JOIN td.destinationWallet dw " +
                        "WHERE t.workspace.id = :workspaceId " + postedFilter() +
                        "AND t.transactionDate BETWEEN :from AND :to " +
                        "ORDER BY t.transactionDate DESC, t.transactionTime DESC, t.createdAt DESC")
                .setParameter("workspaceId", workspaceId)
                .setParameter("from", from)
                .setParameter("to", to)
                .setMaxResults(5)
                .getResultList();
        return rows.stream().map(row -> DashboardRecentTransactionResponse.builder()
                .id((UUID) row[0])
                .type(((TransactionType) row[1]).name())
                .status(((TransactionStatus) row[2]).name())
                .amount((BigDecimal) row[3])
                .transactionDate((LocalDate) row[4])
                .transactionTime((java.time.LocalTime) row[5])
                .description((String) row[6])
                .walletId((UUID) row[7])
                .walletName((String) row[8])
                .categoryId((UUID) row[9])
                .categoryName((String) row[10])
                .sourceWalletName((String) row[11])
                .destinationWalletName((String) row[12])
                .build()).toList();
    }

    private List<DashboardWalletBalanceResponse> walletBalances(UUID workspaceId, UUID userId) {
        return walletService.list(workspaceId, userId).stream()
                .map(wallet -> DashboardWalletBalanceResponse.builder()
                        .walletId(wallet.getId())
                        .walletName(wallet.getName())
                        .walletType(wallet.getType())
                        .currentBalance(wallet.getCurrentBalance())
                        .active(wallet.isActive())
                        .includeInTotal(wallet.isIncludeInTotal())
                        .defaultWallet(wallet.isDefault())
                        .build())
                .toList();
    }

    private List<DashboardCategoryChangeResponse> topCategoryChanges(UUID workspaceId, Period current, Period previous, boolean increases) {
        Map<UUID, CategoryAmount> currentByCategory = categoryAmounts(workspaceId, current.from(), current.to());
        Map<UUID, CategoryAmount> previousByCategory = categoryAmounts(workspaceId, previous.from(), previous.to());
        Set<UUID> categoryIds = new HashSet<>(currentByCategory.keySet());
        categoryIds.addAll(previousByCategory.keySet());

        return categoryIds.stream()
                .map(id -> categoryChange(currentByCategory.get(id), previousByCategory.get(id)))
                .filter(Objects::nonNull)
                .filter(change -> increases
                        ? change.getDelta().compareTo(BigDecimal.ZERO) > 0
                        : change.getDelta().compareTo(BigDecimal.ZERO) < 0)
                .sorted((left, right) -> increases
                        ? right.getDelta().abs().compareTo(left.getDelta().abs())
                        : right.getDelta().abs().compareTo(left.getDelta().abs()))
                .limit(5)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, CategoryAmount> categoryAmounts(UUID workspaceId, LocalDate from, LocalDate to) {
        List<Object[]> rows = entityManager.createQuery(
                "SELECT c.id, c.name, j.id, j.name, SUM(t.amount) " +
                        "FROM Transaction t JOIN t.category c LEFT JOIN c.jar j " +
                        "WHERE t.workspace.id = :workspaceId " + postedFilter() +
                        "AND t.transactionDate BETWEEN :from AND :to " +
                        "AND t.transactionType = :type " +
                        "GROUP BY c.id, c.name, j.id, j.name")
                .setParameter("workspaceId", workspaceId)
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("type", TransactionType.EXPENSE)
                .getResultList();
        return rows.stream()
                .map(row -> new CategoryAmount((UUID) row[0], (String) row[1], (UUID) row[2], (String) row[3], (BigDecimal) row[4]))
                .collect(Collectors.toMap(CategoryAmount::categoryId, Function.identity()));
    }

    private DashboardCategoryChangeResponse categoryChange(CategoryAmount current, CategoryAmount previous) {
        CategoryAmount category = current != null ? current : previous;
        if (category == null) return null;
        BigDecimal currentAmount = current == null ? BigDecimal.ZERO : current.amount();
        BigDecimal previousAmount = previous == null ? BigDecimal.ZERO : previous.amount();
        BigDecimal delta = currentAmount.subtract(previousAmount);
        if (delta.compareTo(BigDecimal.ZERO) == 0) return null;
        Change change = change(currentAmount, previousAmount);
        return DashboardCategoryChangeResponse.builder()
                .categoryId(category.categoryId())
                .categoryName(category.categoryName())
                .jarId(category.jarId())
                .jarName(category.jarName())
                .currentAmount(currentAmount)
                .previousAmount(previousAmount)
                .delta(delta)
                .percent(change.value())
                .newCategory(change.isNew())
                .build();
    }

    private DashboardComparisonResponse comparison(Totals current, Totals previous) {
        return comparison(current, previous, null);
    }

    private DashboardComparisonResponse comparison(Totals current, Totals previous, Period previousPeriod) {
        Change incomeChange = change(current.income(), previous.income());
        Change expenseChange = change(current.expense(), previous.expense());
        Change netChange = change(current.net(), previous.net());
        return DashboardComparisonResponse.builder()
                .currentAmount(current.expense())
                .previousAmount(previous.expense())
                .percentageDifference(expenseChange.valueOrZero())
                .status(status(current.expense(), previous.expense()))
                .previousStartDate(previousPeriod == null ? null : previousPeriod.from())
                .previousEndDate(previousPeriod == null ? null : previousPeriod.to())
                .previousIncome(previous.income())
                .previousExpense(previous.expense())
                .previousNetCashFlow(previous.net())
                .incomeDelta(current.income().subtract(previous.income()))
                .expenseDelta(current.expense().subtract(previous.expense()))
                .netDelta(current.net().subtract(previous.net()))
                .incomePercent(incomeChange.value())
                .expensePercent(expenseChange.value())
                .netPercent(netChange.value())
                .incomeNew(incomeChange.isNew())
                .expenseNew(expenseChange.isNew())
                .netNew(netChange.isNew())
                .incomeLabel(incomeChange.label())
                .expenseLabel(expenseChange.label())
                .netLabel(netChange.label())
                .build();
    }

    private Change change(BigDecimal current, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return new Change(null, current.compareTo(BigDecimal.ZERO) == 0 ? "Không có dữ liệu" : "Phát sinh mới", current.compareTo(BigDecimal.ZERO) > 0);
        }
        double pct = current.subtract(previous).divide(previous.abs(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue();
        return new Change(pct, null, false);
    }

    private String status(BigDecimal current, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) return current.compareTo(BigDecimal.ZERO) > 0 ? "NEW" : "NO_DATA";
        int cmp = current.compareTo(previous);
        if (cmp > 0) return "INCREASE";
        if (cmp < 0) return "DECREASE";
        return "UNCHANGED";
    }

    private double percent(BigDecimal amount, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) == 0) return 0.0;
        return amount.divide(total, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue();
    }

    private YearMonth parseMonth(String month, Workspace workspace) {
        if (month == null || month.isBlank()) return YearMonth.from(today(workspace));
        return YearMonth.parse(month.trim());
    }

    private ComparisonMode parseMode(String value) {
        if (value == null || value.isBlank()) return ComparisonMode.SAME_PERIOD;
        return ComparisonMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private Period currentPeriod(YearMonth month, ComparisonMode mode, Workspace workspace) {
        LocalDate from = month.atDay(1);
        if (mode == ComparisonMode.FULL_MONTH) return new Period(from, month.atEndOfMonth());
        LocalDate today = today(workspace);
        int day = month.equals(YearMonth.from(today)) ? today.getDayOfMonth() : month.lengthOfMonth();
        return new Period(from, month.atDay(Math.min(day, month.lengthOfMonth())));
    }

    private Period previousPeriod(Period current, ComparisonMode mode) {
        YearMonth previousMonth = YearMonth.from(current.from()).minusMonths(1);
        LocalDate from = previousMonth.atDay(1);
        if (mode == ComparisonMode.FULL_MONTH) return new Period(from, previousMonth.atEndOfMonth());
        return new Period(from, previousMonth.atDay(Math.min(current.to().getDayOfMonth(), previousMonth.lengthOfMonth())));
    }

    private ZoneId zone(String timezone) {
        try {
            return ZoneId.of(timezone == null || timezone.isBlank() ? FALLBACK_ZONE : timezone.trim());
        } catch (Exception ex) {
            return ZoneId.of(FALLBACK_ZONE);
        }
    }

    private LocalDate today(Workspace workspace) {
        return LocalDate.now(clock.withZone(zone(workspace.getTimezone())));
    }

    private String postedFilter() {
        return "AND t.transactionStatus = com.moneyflowbackend.transaction.model.TransactionStatus.POSTED " +
                "AND t.deletedAt IS NULL ";
    }

    private String ledgerFilter() {
        return postedFilter() + "AND t.affectsWalletBalance = true ";
    }

    private enum ComparisonMode { SAME_PERIOD, FULL_MONTH }
    private record Period(LocalDate from, LocalDate to) {}
    private record Totals(BigDecimal income, BigDecimal expense, BigDecimal net, long count) {}
    private record CategoryAmount(UUID categoryId, String categoryName, UUID jarId, String jarName, BigDecimal amount) {}
    private record Change(Double value, String label, boolean isNew) {
        double valueOrZero() { return value == null ? 0.0 : Math.abs(value); }
    }
}
