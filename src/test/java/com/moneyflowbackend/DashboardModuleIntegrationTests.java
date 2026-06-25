package com.moneyflowbackend;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.dashboard.dto.DashboardResponse;
import com.moneyflowbackend.dashboard.service.DashboardService;
import com.moneyflowbackend.jar.model.Jar;
import com.moneyflowbackend.jar.repository.JarRepository;
import com.moneyflowbackend.transaction.model.Transaction;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DashboardModuleIntegrationTests {
    @Autowired DashboardService dashboardService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired JarRepository jarRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired TransferDetailRepository transferDetailRepository;

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-06-15T01:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Test
    void dashboardAggregatesPostedAnalyticsRowsWhileWalletTotalUsesKnownWalletLedger() {
        TestContext ctx = context("dash_core", "Asia/Ho_Chi_Minh");
        TestContext other = context("dash_other", "Asia/Ho_Chi_Minh");
        Wallet cash = wallet(ctx, "Cash", WalletType.CASH, true, true, true, "1000000");
        Wallet bank = wallet(ctx, "Bank", WalletType.BANK, false, true, true, "2000000");
        wallet(ctx, "Hidden", WalletType.BANK, false, true, false, "9999999");
        wallet(ctx, "Inactive", WalletType.CASH, false, false, true, "8888888");
        Jar nec = jar(ctx, "NEC", "Needs", "55");
        Jar edu = jar(ctx, "EDU", "Education", "10");
        Category salary = category(ctx, "Salary", CategoryType.INCOME, null, false);
        Category food = category(ctx, "Food", CategoryType.EXPENSE, nec, false);
        Category course = category(ctx, "Old Course", CategoryType.EXPENSE, edu, true);
        Category otherSalary = category(other, "Other Salary", CategoryType.INCOME, null, false);
        Wallet otherWallet = wallet(other, "Other Cash", WalletType.CASH, true, true, true, "0");

        tx(ctx, cash, salary, TransactionType.INCOME, TransactionStatus.POSTED, "5000000", "2026-06-02", "09:00", false, false, "Salary");
        tx(ctx, cash, food, TransactionType.EXPENSE, TransactionStatus.POSTED, "700000", "2026-06-03", "12:00", false, false, "Lunch");
        tx(ctx, bank, course, TransactionType.EXPENSE, TransactionStatus.POSTED, "300000", "2026-06-04", "13:00", false, false, "Course");
        tx(ctx, cash, food, TransactionType.EXPENSE, TransactionStatus.PLANNED, "500000", "2026-06-05", "14:00", false, false, "Planned");
        tx(ctx, cash, food, TransactionType.EXPENSE, TransactionStatus.POSTED, "111111", "2026-06-06", "15:00", true, false, "Deleted");
        tx(ctx, cash, food, TransactionType.EXPENSE, TransactionStatus.POSTED, "222222", "2026-06-07", "16:00", false, true, "Unknown wallet");
        transfer(ctx, bank, cash, "1000000", "2026-06-08");
        tx(other, otherWallet, otherSalary, TransactionType.INCOME, TransactionStatus.POSTED, "9999999", "2026-06-02", "09:00", false, false, "Other");

        DashboardResponse dashboard = dashboardService.getDashboard(ctx.workspace().getId(), "2026-06", "FULL_MONTH", ctx.user().getId());

        assertThat(dashboard.getIncome()).isEqualByComparingTo("5000000");
        assertThat(dashboard.getExpense()).isEqualByComparingTo("1222222");
        assertThat(dashboard.getNetCashFlow()).isEqualByComparingTo("3777778");
        assertThat(dashboard.getTransactionCount()).isEqualTo(4);
        assertThat(dashboard.getWalletTotal()).isEqualByComparingTo("7000000");
        assertThat(dashboard.getExpenseByCategory()).extracting("categoryName").containsExactly("Food", "Old Course");
        assertThat(dashboard.getExpenseByJar()).extracting("jarCode").containsExactly("NEC", "EDU");
        assertThat(dashboard.getIncomeByCategory().getFirst().getAmount()).isEqualByComparingTo("5000000");
        assertThat(dashboard.getRecentTransactions()).extracting("description").containsExactly("Transfer", "Unknown wallet", "Course", "Lunch", "Salary");
    }

    @Test
    void comparisonModesHandleSamePeriodFullMonthPreviousZeroAndTimezoneDefaultMonth() {
        TestContext ctx = context("dash_compare", "Asia/Ho_Chi_Minh");
        Wallet cash = wallet(ctx, "Cash", WalletType.CASH, true, true, true, "0");
        Category income = category(ctx, "Income", CategoryType.INCOME, null, false);
        Category expense = category(ctx, "Expense", CategoryType.EXPENSE, jar(ctx, "NEC", "Needs", "55"), false);

        tx(ctx, cash, income, TransactionType.INCOME, TransactionStatus.POSTED, "1000", "2026-06-01", "09:00", false, false, "Current income");
        tx(ctx, cash, expense, TransactionType.EXPENSE, TransactionStatus.POSTED, "400", "2026-06-15", "09:00", false, false, "Current expense");
        tx(ctx, cash, income, TransactionType.INCOME, TransactionStatus.POSTED, "500", "2026-05-01", "09:00", false, false, "Previous income");
        tx(ctx, cash, expense, TransactionType.EXPENSE, TransactionStatus.POSTED, "100", "2026-05-15", "09:00", false, false, "Previous expense");
        tx(ctx, cash, expense, TransactionType.EXPENSE, TransactionStatus.POSTED, "900", "2026-05-31", "09:00", false, false, "Previous tail");

        DashboardResponse same = dashboardService.getDashboard(ctx.workspace().getId(), "2026-06", "SAME_PERIOD", ctx.user().getId());
        DashboardResponse full = dashboardService.getDashboard(ctx.workspace().getId(), "2026-06", "FULL_MONTH", ctx.user().getId());
        DashboardResponse zero = dashboardService.getDashboard(ctx.workspace().getId(), "2026-04", "FULL_MONTH", ctx.user().getId());
        DashboardResponse defaultMonth = dashboardService.getDashboard(ctx.workspace().getId(), null, null, ctx.user().getId());

        assertThat(same.getPeriod().getDateTo()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(same.getComparison().getPreviousExpense()).isEqualByComparingTo("100");
        assertThat(full.getComparison().getPreviousExpense()).isEqualByComparingTo("1000");
        assertThat(zero.getComparison().getExpensePercent()).isNull();
        assertThat(zero.getComparison().getExpenseLabel()).isEqualTo("Khong co du lieu");
        assertThat(defaultMonth.getPeriod().getMonth()).isEqualTo("2026-06");
    }

    private TestContext context(String prefix, String timezone) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .fullName("Dashboard Test")
                .build());
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .name(prefix + " workspace")
                .createdByUser(user)
                .timezone(timezone)
                .build());
        workspaceMemberRepository.save(WorkspaceMember.builder().workspace(workspace).user(user).role(WorkspaceRole.OWNER).build());
        return new TestContext(user, workspace);
    }

    private Wallet wallet(TestContext ctx, String name, WalletType type, boolean isDefault, boolean active, boolean include, String opening) {
        return walletRepository.saveAndFlush(Wallet.builder()
                .workspace(ctx.workspace())
                .name(name)
                .walletType(type)
                .openingBalance(new BigDecimal(opening))
                .isDefault(isDefault)
                .isActive(active)
                .includeInTotal(include)
                .build());
    }

    private Jar jar(TestContext ctx, String code, String name, String allocation) {
        return jarRepository.saveAndFlush(Jar.builder()
                .workspace(ctx.workspace())
                .code(code)
                .name(name)
                .allocationPercent(new BigDecimal(allocation))
                .build());
    }

    private Category category(TestContext ctx, String name, CategoryType type, Jar jar, boolean archived) {
        return categoryRepository.saveAndFlush(Category.builder()
                .workspace(ctx.workspace())
                .name(name)
                .categoryType(type)
                .jar(jar)
                .isActive(!archived)
                .isArchived(archived)
                .build());
    }

    private Transaction tx(TestContext ctx, Wallet wallet, Category category, TransactionType type, TransactionStatus status,
                           String amount, String date, String time, boolean deleted, boolean walletUnknown, String description) {
        return transactionRepository.saveAndFlush(Transaction.builder()
                .workspace(ctx.workspace())
                .createdByUser(ctx.user())
                .wallet(wallet)
                .category(category)
                .transactionType(type)
                .transactionStatus(status)
                .amount(new BigDecimal(amount))
                .currency("VND")
                .transactionDate(LocalDate.parse(date))
                .transactionTime(LocalTime.parse(time))
                .description(description)
                .walletUnknown(walletUnknown)
                .historical(walletUnknown)
                .affectsWalletBalance(!walletUnknown)
                .deletedAt(deleted ? Instant.now() : null)
                .build());
    }

    private void transfer(TestContext ctx, Wallet source, Wallet destination, String amount, String date) {
        Transaction tx = tx(ctx, source, null, TransactionType.TRANSFER, TransactionStatus.POSTED, amount, date, "17:00", false, false, "Transfer");
        transferDetailRepository.saveAndFlush(TransferDetail.builder()
                .transactionId(tx.getId())
                .transaction(tx)
                .sourceWallet(source)
                .destinationWallet(destination)
                .build());
    }

    private record TestContext(User user, Workspace workspace) {}
}
