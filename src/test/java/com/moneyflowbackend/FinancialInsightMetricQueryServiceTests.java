package com.moneyflowbackend;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.income.model.IncomeSource;
import com.moneyflowbackend.income.repository.IncomeSourceRepository;
import com.moneyflowbackend.insight.dto.MetricBreakdownRow;
import com.moneyflowbackend.insight.service.FinancialMetricQueryService;
import com.moneyflowbackend.jar.model.Jar;
import com.moneyflowbackend.jar.repository.JarRepository;
import com.moneyflowbackend.transaction.model.AdjustmentDirection;
import com.moneyflowbackend.transaction.model.Transaction;
import com.moneyflowbackend.transaction.model.TransactionSourceType;
import com.moneyflowbackend.transaction.model.TransactionStatus;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.transaction.model.TransferDetail;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.transaction.repository.TransferDetailRepository;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.model.WalletType;
import com.moneyflowbackend.wallet.repository.WalletRepository;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FinancialInsightMetricQueryServiceTests {
    @Autowired FinancialMetricQueryService metricQueryService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired JarRepository jarRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired IncomeSourceRepository incomeSourceRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired TransferDetailRepository transferDetailRepository;

    @Test
    void periodMetricsUsePostedIncomeExpenseOnlyAndKeepWorkspaceIsolation() {
        TestContext ctx = context("insight_metrics");
        TestContext other = context("insight_other");
        Wallet cash = wallet(ctx, "Cash");
        Wallet bank = wallet(ctx, "Bank");
        Wallet otherWallet = wallet(other, "Other cash");
        Jar needs = jar(ctx, "NEC", "Needs");
        Jar travel = jar(ctx, "TRV", "Travel");
        Category food = category(ctx, "Food", needs);
        Category fuel = category(ctx, "Fuel", needs);
        Category flight = category(ctx, "Flight", travel);
        Category noJar = category(ctx, "No jar", null);
        Category otherCategory = category(other, "Other food", null);
        IncomeSource salary = source(ctx, "Salary");

        tx(ctx, cash, food, salary, TransactionType.INCOME, TransactionStatus.POSTED, "1000000", "2026-07-01", true, false, false, null);
        tx(ctx, null, null, null, TransactionType.INCOME, TransactionStatus.POSTED, "800000", "2026-07-02", false, true, false, null);
        tx(ctx, cash, food, null, TransactionType.EXPENSE, TransactionStatus.POSTED, "120000", "2026-07-03", true, false, false, null);
        tx(ctx, cash, food, null, TransactionType.EXPENSE, TransactionStatus.POSTED, "80000", "2026-07-04", true, false, false, null);
        tx(ctx, bank, fuel, null, TransactionType.EXPENSE, TransactionStatus.POSTED, "50000", "2026-07-05", true, false, false, null);
        tx(ctx, bank, flight, null, TransactionType.EXPENSE, TransactionStatus.POSTED, "300000", "2026-07-31", true, false, false, null);
        tx(ctx, bank, noJar, null, TransactionType.EXPENSE, TransactionStatus.POSTED, "40000", "2026-07-06", true, false, false, null);
        tx(ctx, bank, null, null, TransactionType.EXPENSE, TransactionStatus.POSTED, "60000", "2026-07-07", true, false, false, null);
        tx(ctx, cash, food, null, TransactionType.EXPENSE, TransactionStatus.DRAFT, "999999", "2026-07-08", true, false, false, null);
        tx(ctx, cash, food, null, TransactionType.EXPENSE, TransactionStatus.PLANNED, "999999", "2026-07-09", true, false, false, null);
        tx(ctx, cash, food, null, TransactionType.EXPENSE, TransactionStatus.POSTED, "999999", "2026-07-10", true, false, true, null);
        tx(ctx, cash, food, null, TransactionType.LOAN_COLLECTION, TransactionStatus.POSTED, "70000", "2026-07-11", true, false, false, null);
        tx(ctx, cash, food, null, TransactionType.LOAN_DISBURSEMENT, TransactionStatus.POSTED, "90000", "2026-07-12", true, false, false, null);
        tx(ctx, cash, food, null, TransactionType.ADJUSTMENT, TransactionStatus.POSTED, "30000", "2026-07-13", true, false, false, AdjustmentDirection.INCREASE);
        transfer(ctx, bank, cash, "250000", "2026-07-14");
        tx(ctx, cash, food, null, TransactionType.INCOME, TransactionStatus.POSTED, "123456", "2026-06-30", true, false, false, null);
        tx(other, otherWallet, otherCategory, null, TransactionType.INCOME, TransactionStatus.POSTED, "999999", "2026-07-01", true, false, false, null);

        var totals = metricQueryService.getPeriodTotals(ctx.workspace().getId(), LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-31"));
        var noWallet = metricQueryService.getNoWalletIncome(ctx.workspace().getId(), LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-31"));
        var wallet = metricQueryService.getWalletAffectingSummary(ctx.workspace().getId(), LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-31"));

        assertThat(totals.totalIncome()).isEqualByComparingTo("1800000");
        assertThat(totals.totalExpense()).isEqualByComparingTo("650000");
        assertThat(totals.netCashflow()).isEqualByComparingTo("1150000");
        assertThat(totals.transactionCount()).isEqualTo(8);
        assertThat(totals.incomeTransactionCount()).isEqualTo(2);
        assertThat(totals.expenseTransactionCount()).isEqualTo(6);
        assertThat(totals.currency()).isEqualTo("VND");
        assertThat(noWallet.totalAmount()).isEqualByComparingTo("800000");
        assertThat(noWallet.count()).isEqualTo(1);
        assertThat(noWallet.sampleTransactionIds()).hasSize(1);
        assertThat(wallet.walletAffectingIncome()).isEqualByComparingTo("1070000");
        assertThat(wallet.walletAffectingExpense()).isEqualByComparingTo("740000");
        assertThat(wallet.nonWalletIncome()).isEqualByComparingTo("800000");
        assertThat(wallet.transferIn()).isEqualByComparingTo("250000");
        assertThat(wallet.transferOut()).isEqualByComparingTo("250000");
        assertThat(wallet.snapshotAdjustments()).isEqualByComparingTo("30000");
    }

    @Test
    void breakdownsGroupMissingSourceCategoryAndJar() {
        TestContext ctx = context("insight_breakdowns");
        Wallet cash = wallet(ctx, "Cash");
        Jar needs = jar(ctx, "NEC", "Needs");
        Category food = category(ctx, "Food", needs);
        Category uncapped = category(ctx, "Loose", null);
        IncomeSource salary = source(ctx, "Salary");

        tx(ctx, cash, food, salary, TransactionType.INCOME, TransactionStatus.POSTED, "1000", "2026-08-01", true, false, false, null);
        tx(ctx, null, null, null, TransactionType.INCOME, TransactionStatus.POSTED, "500", "2026-08-01", false, true, false, null);
        tx(ctx, cash, food, null, TransactionType.EXPENSE, TransactionStatus.POSTED, "200", "2026-08-01", true, false, false, null);
        tx(ctx, cash, food, null, TransactionType.EXPENSE, TransactionStatus.POSTED, "100", "2026-08-02", true, false, false, null);
        tx(ctx, cash, uncapped, null, TransactionType.EXPENSE, TransactionStatus.POSTED, "50", "2026-08-02", true, false, false, null);
        tx(ctx, cash, null, null, TransactionType.EXPENSE, TransactionStatus.POSTED, "150", "2026-08-02", true, false, false, null);

        List<MetricBreakdownRow> incomeBySource = metricQueryService.getIncomeBySource(ctx.workspace().getId(), LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"));
        List<MetricBreakdownRow> byCategory = metricQueryService.getExpenseByCategory(ctx.workspace().getId(), LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"));
        List<MetricBreakdownRow> byJar = metricQueryService.getExpenseByJar(ctx.workspace().getId(), LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"));
        MetricBreakdownRow uncategorized = metricQueryService.getUncategorizedExpense(ctx.workspace().getId(), LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"));

        assertThat(incomeBySource).extracting(MetricBreakdownRow::name).containsExactly("Salary", "Chưa rõ nguồn");
        assertThat(byCategory).anySatisfy(row -> {
            assertThat(row.name()).isEqualTo("Food");
            assertThat(row.amount()).isEqualByComparingTo("300");
            assertThat(row.count()).isEqualTo(2);
            assertThat(row.percentage()).isEqualByComparingTo("60.00");
        });
        assertThat(byCategory).anySatisfy(row -> {
            assertThat(row.name()).isEqualTo("Chưa phân loại");
            assertThat(row.amount()).isEqualByComparingTo("150");
        });
        assertThat(byJar).anySatisfy(row -> {
            assertThat(row.name()).isEqualTo("Needs");
            assertThat(row.amount()).isEqualByComparingTo("300");
        });
        assertThat(byJar).anySatisfy(row -> {
            assertThat(row.name()).isEqualTo("Chưa có hũ");
            assertThat(row.amount()).isEqualByComparingTo("50");
        });
        assertThat(byJar).anySatisfy(row -> {
            assertThat(row.name()).isEqualTo("Chưa phân loại");
            assertThat(row.amount()).isEqualByComparingTo("150");
        });
        assertThat(uncategorized.amount()).isEqualByComparingTo("150");
        assertThat(uncategorized.count()).isEqualTo(1);
        assertThat(uncategorized.percentage()).isEqualByComparingTo("30.00");
    }

    @Test
    void emptyAndInvalidRangesAreExplicit() {
        TestContext ctx = context("insight_empty");

        var totals = metricQueryService.getPeriodTotals(ctx.workspace().getId(), LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-01"));

        assertThat(totals.totalIncome()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(totals.totalExpense()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(totals.transactionCount()).isZero();
        assertThat(metricQueryService.getExpenseByCategory(ctx.workspace().getId(), LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-01"))).isEmpty();
        assertThatThrownBy(() -> metricQueryService.getPeriodTotals(ctx.workspace().getId(), LocalDate.parse("2026-09-02"), LocalDate.parse("2026-09-01")))
                .isInstanceOf(BusinessException.class);
    }

    private TestContext context(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .fullName("Insight Test")
                .build());
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .name(prefix + " workspace")
                .createdByUser(user)
                .currency("VND")
                .build());
        workspaceMemberRepository.save(WorkspaceMember.builder().workspace(workspace).user(user).role(WorkspaceRole.OWNER).build());
        return new TestContext(user, workspace);
    }

    private Wallet wallet(TestContext ctx, String name) {
        return walletRepository.saveAndFlush(Wallet.builder()
                .workspace(ctx.workspace())
                .name(name)
                .walletType(WalletType.CASH)
                .openingBalance(BigDecimal.ZERO)
                .build());
    }

    private Jar jar(TestContext ctx, String code, String name) {
        return jarRepository.saveAndFlush(Jar.builder()
                .workspace(ctx.workspace())
                .code(code)
                .name(name)
                .allocationPercent(BigDecimal.ZERO)
                .build());
    }

    private Category category(TestContext ctx, String name, Jar jar) {
        return categoryRepository.saveAndFlush(Category.builder()
                .workspace(ctx.workspace())
                .name(name)
                .categoryType(CategoryType.EXPENSE)
                .jar(jar)
                .build());
    }

    private IncomeSource source(TestContext ctx, String name) {
        return incomeSourceRepository.saveAndFlush(IncomeSource.builder()
                .workspace(ctx.workspace())
                .createdByUser(ctx.user())
                .name(name)
                .build());
    }

    private Transaction tx(TestContext ctx, Wallet wallet, Category category, IncomeSource source, TransactionType type,
                           TransactionStatus status, String amount, String date, boolean affectsWallet, boolean historical,
                           boolean deleted, AdjustmentDirection adjustmentDirection) {
        return transactionRepository.saveAndFlush(Transaction.builder()
                .workspace(ctx.workspace())
                .createdByUser(ctx.user())
                .wallet(wallet)
                .category(category)
                .incomeSource(source)
                .transactionType(type)
                .adjustmentDirection(adjustmentDirection)
                .transactionStatus(status)
                .amount(new BigDecimal(amount))
                .currency("VND")
                .transactionDate(LocalDate.parse(date))
                .transactionTime(LocalTime.NOON)
                .sourceType(historical ? TransactionSourceType.EXCEL_MIGRATION : TransactionSourceType.MANUAL)
                .historical(historical)
                .affectsWalletBalance(affectsWallet)
                .deletedAt(deleted ? Instant.now() : null)
                .build());
    }

    private void transfer(TestContext ctx, Wallet source, Wallet destination, String amount, String date) {
        Transaction tx = tx(ctx, source, null, null, TransactionType.TRANSFER, TransactionStatus.POSTED, amount, date, true, false, false, null);
        transferDetailRepository.saveAndFlush(TransferDetail.builder()
                .transactionId(tx.getId())
                .transaction(tx)
                .sourceWallet(source)
                .destinationWallet(destination)
                .build());
    }

    private record TestContext(User user, Workspace workspace) {
    }
}
