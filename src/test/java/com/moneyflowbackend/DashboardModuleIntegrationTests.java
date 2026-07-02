package com.moneyflowbackend;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.dashboard.dto.DashboardResponse;
import com.moneyflowbackend.dashboard.service.DashboardService;
import com.moneyflowbackend.jar.model.Jar;
import com.moneyflowbackend.jar.repository.JarRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void dashboardMonthly_excludesTransfersAndDeletedTransactions_includesHistoricalMigrationAndUsesWalletLedger() {
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
        tx(ctx, cash, food, TransactionType.EXPENSE, TransactionStatus.POSTED, "1000000", "2026-05-04", "13:00", false, true, "Previous food");
        tx(ctx, cash, food, TransactionType.EXPENSE, TransactionStatus.PLANNED, "500000", "2026-06-05", "14:00", false, false, "Planned");
        tx(ctx, cash, food, TransactionType.EXPENSE, TransactionStatus.POSTED, "111111", "2026-06-06", "15:00", true, false, "Deleted");
        tx(ctx, cash, food, TransactionType.EXPENSE, TransactionStatus.POSTED, "222222", "2026-06-07", "16:00", false, true, "Unknown wallet");
        transfer(ctx, bank, cash, "1000000", "2026-06-08");
        tx(other, otherWallet, otherSalary, TransactionType.INCOME, TransactionStatus.POSTED, "9999999", "2026-06-02", "09:00", false, false, "Other");

        DashboardResponse dashboard = dashboardService.getDashboard(ctx.workspace().getId(), "2026-06", "FULL_MONTH", ctx.user().getId());

        assertThat(dashboard.getIncome()).isEqualByComparingTo("5000000");
        assertThat(dashboard.getSummary().getIncome()).isEqualByComparingTo("5000000");
        assertThat(dashboard.getSummary().getExpense()).isEqualByComparingTo("1222222");
        assertThat(dashboard.getSummary().getNet()).isEqualByComparingTo("3777778");
        assertThat(dashboard.getSummary().getTransactionCount()).isEqualTo(4);
        assertThat(dashboard.getExpense()).isEqualByComparingTo("1222222");
        assertThat(dashboard.getNetCashFlow()).isEqualByComparingTo("3777778");
        assertThat(dashboard.getTransactionCount()).isEqualTo(4);
        assertThat(dashboard.getWalletTotal()).isEqualByComparingTo("7000000");
        assertThat(dashboard.getExpenseByCategory()).extracting("categoryName").containsExactly("Food", "Old Course");
        assertThat(dashboard.getCategoryBreakdown()).extracting("categoryName").containsExactly("Food", "Old Course");
        assertThat(dashboard.getExpenseByJar()).extracting("jarCode").containsExactly("NEC", "EDU");
        assertThat(dashboard.getJarBreakdown()).extracting("jarCode").containsExactly("NEC", "EDU");
        assertThat(dashboard.getIncomeByCategory().getFirst().getAmount()).isEqualByComparingTo("5000000");
        assertThat(dashboard.getWalletBalances()).extracting("walletName").contains("Cash", "Bank");
        assertThat(dashboard.getTopIncreases()).extracting("categoryName").contains("Old Course");
        assertThat(dashboard.getTopDecreases()).extracting("categoryName").contains("Food");
        assertThat(dashboard.getRecentTransactions()).extracting("description").containsExactly("Transfer", "Unknown wallet", "Course", "Lunch", "Salary");
        assertThat(dashboard.getRecentTransactions().getFirst().getSourceWalletName()).isEqualTo("Bank");
        assertThat(dashboard.getRecentTransactions().getFirst().getDestinationWalletName()).isEqualTo("Cash");
        assertThatThrownBy(() -> dashboardService.getDashboard(ctx.workspace().getId(), "2026-06", "FULL_MONTH", other.user().getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void dashboardMonthly_samePeriodComparisonUsesFixedClockAndPreviousZeroMarksNew() {
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
        DashboardResponse previousZero = dashboardService.getDashboard(ctx.workspace().getId(), "2026-05", "FULL_MONTH", ctx.user().getId());
        DashboardResponse zero = dashboardService.getDashboard(ctx.workspace().getId(), "2026-04", "FULL_MONTH", ctx.user().getId());
        DashboardResponse defaultMonth = dashboardService.getDashboard(ctx.workspace().getId(), null, null, ctx.user().getId());

        assertThat(same.getPeriod().getDateTo()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(same.getComparison().getPreviousStartDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(same.getComparison().getPreviousEndDate()).isEqualTo(LocalDate.of(2026, 5, 15));
        assertThat(same.getComparison().getPreviousExpense()).isEqualByComparingTo("100");
        assertThat(full.getComparison().getPreviousExpense()).isEqualByComparingTo("1000");
        assertThat(previousZero.getComparison().isIncomeNew()).isTrue();
        assertThat(previousZero.getComparison().isExpenseNew()).isTrue();
        assertThat(zero.getComparison().getExpensePercent()).isNull();
        assertThat(zero.getComparison().getExpenseLabel()).isEqualTo("Không có dữ liệu");
        assertThat(zero.getComparison().isExpenseNew()).isFalse();
        assertThat(defaultMonth.getPeriod().getMonth()).isEqualTo("2026-06");
    }

    @Test
    void dashboardMonthly_withoutCreatedByIncludesAllMembers() {
        SharedDashboardContext ctx = sharedContext("dash_all");

        DashboardResponse dashboard = dashboardService.getDashboard(ctx.workspace().getId(), "2026-06", "FULL_MONTH", ctx.owner().getId());

        assertThat(dashboard.getIncome()).isEqualByComparingTo("3000");
        assertThat(dashboard.getExpense()).isEqualByComparingTo("1000");
        assertThat(dashboard.getTransactionCount()).isEqualTo(4);
    }

    @Test
    void dashboardMonthly_withCreatedByFiltersOneMember() {
        SharedDashboardContext ctx = sharedContext("dash_filter");

        DashboardResponse dashboard = dashboardService.getDashboard(ctx.workspace().getId(), "2026-06", "FULL_MONTH", ctx.owner().getId(), ctx.member().getId());

        assertThat(dashboard.getIncome()).isEqualByComparingTo("2000");
        assertThat(dashboard.getExpense()).isEqualByComparingTo("700");
        assertThat(dashboard.getTransactionCount()).isEqualTo(2);
        assertThat(dashboard.getRecentTransactions()).extracting("description").containsExactly("Member expense", "Member income");
    }

    @Test
    void dashboardMonthly_createdByMustBelongToWorkspace() {
        SharedDashboardContext ctx = sharedContext("dash_member_guard");
        TestContext other = context("dash_member_other", "Asia/Ho_Chi_Minh");

        assertThatThrownBy(() -> dashboardService.getDashboard(ctx.workspace().getId(), "2026-06", "FULL_MONTH", ctx.owner().getId(), other.user().getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void dashboardMonthly_memberBreakdownAggregatesByCreator() {
        SharedDashboardContext ctx = sharedContext("dash_breakdown");

        DashboardResponse dashboard = dashboardService.getDashboard(ctx.workspace().getId(), "2026-06", "FULL_MONTH", ctx.owner().getId());

        assertThat(dashboard.getMemberBreakdown()).hasSize(2);
        assertThat(dashboard.getMemberBreakdown())
                .anySatisfy(member -> {
                    assertThat(member.getUserId()).isEqualTo(ctx.owner().getId());
                    assertThat(member.getIncome()).isEqualByComparingTo("1000");
                    assertThat(member.getExpense()).isEqualByComparingTo("300");
                    assertThat(member.getNet()).isEqualByComparingTo("700");
                    assertThat(member.getTransactionCount()).isEqualTo(2);
                })
                .anySatisfy(member -> {
                    assertThat(member.getUserId()).isEqualTo(ctx.member().getId());
                    assertThat(member.getIncome()).isEqualByComparingTo("2000");
                    assertThat(member.getExpense()).isEqualByComparingTo("700");
                    assertThat(member.getNet()).isEqualByComparingTo("1300");
                    assertThat(member.getTransactionCount()).isEqualTo(2);
                });
    }

    @Test
    void dashboardMonthly_memberBreakdownExcludesTransfers() {
        SharedDashboardContext ctx = sharedContext("dash_transfer");

        DashboardResponse dashboard = dashboardService.getDashboard(ctx.workspace().getId(), "2026-06", "FULL_MONTH", ctx.owner().getId());

        assertThat(dashboard.getMemberBreakdown())
                .extracting("transactionCount")
                .containsExactlyInAnyOrder(2L, 2L);
    }

    @Test
    void dashboardMonthly_memberBreakdownExcludesSoftDeleted() {
        SharedDashboardContext ctx = sharedContext("dash_deleted");

        DashboardResponse dashboard = dashboardService.getDashboard(ctx.workspace().getId(), "2026-06", "FULL_MONTH", ctx.owner().getId());

        assertThat(dashboard.getMemberBreakdown())
                .filteredOn(member -> member.getUserId().equals(ctx.owner().getId()))
                .first()
                .extracting("expense")
                .isEqualTo(new BigDecimal("300.00"));
    }

    @Test
    void dashboardMonthly_memberBreakdownDoesNotExposeEmail() {
        SharedDashboardContext ctx = sharedContext("dash_privacy");

        DashboardResponse dashboard = dashboardService.getDashboard(ctx.workspace().getId(), "2026-06", "FULL_MONTH", ctx.owner().getId());

        assertThat(dashboard.getMemberBreakdown().getFirst()).hasNoNullFieldsOrProperties();
        assertThat(dashboard.getMemberBreakdown().getFirst().getUsername()).doesNotContain("@");
    }

    @Test
    void dashboardMonthly_viewerCanReadSharedDashboardIfPolicyAllows() {
        SharedDashboardContext ctx = sharedContext("dash_viewer");

        DashboardResponse dashboard = dashboardService.getDashboard(ctx.workspace().getId(), "2026-06", "FULL_MONTH", ctx.viewer().getId());

        assertThat(dashboard.getIncome()).isEqualByComparingTo("3000");
        assertThat(dashboard.getMemberBreakdown()).hasSize(2);
    }

    @Test
    void dashboardMonthly_crossWorkspaceCreatedByBlocked() {
        SharedDashboardContext ctx = sharedContext("dash_cross");
        SharedDashboardContext other = sharedContext("dash_cross_other");

        assertThatThrownBy(() -> dashboardService.getDashboard(ctx.workspace().getId(), "2026-06", "FULL_MONTH", ctx.owner().getId(), other.member().getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void dashboardMonthly_personalWorkspaceStillWorks() {
        TestContext ctx = context("dash_personal", "Asia/Ho_Chi_Minh");
        Wallet cash = wallet(ctx, "Cash", WalletType.CASH, true, true, true, "0");
        Category income = category(ctx, "Income", CategoryType.INCOME, null, false);
        Category expense = category(ctx, "Expense", CategoryType.EXPENSE, null, false);
        tx(ctx, cash, income, TransactionType.INCOME, TransactionStatus.POSTED, "1000", "2026-06-01", "09:00", false, false, "Income");
        tx(ctx, cash, expense, TransactionType.EXPENSE, TransactionStatus.POSTED, "400", "2026-06-02", "09:00", false, false, "Expense");

        DashboardResponse dashboard = dashboardService.getDashboard(ctx.workspace().getId(), "2026-06", "FULL_MONTH", ctx.user().getId(), ctx.user().getId());

        assertThat(dashboard.getIncome()).isEqualByComparingTo("1000");
        assertThat(dashboard.getExpense()).isEqualByComparingTo("400");
        assertThat(dashboard.getMemberBreakdown()).singleElement().satisfies(member -> {
            assertThat(member.getUserId()).isEqualTo(ctx.user().getId());
            assertThat(member.getTransactionCount()).isEqualTo(2);
        });
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
        return tx(ctx, ctx.user(), wallet, category, type, status, amount, date, time, deleted, walletUnknown, description);
    }

    private Transaction tx(TestContext ctx, User createdBy, Wallet wallet, Category category, TransactionType type, TransactionStatus status,
                           String amount, String date, String time, boolean deleted, boolean walletUnknown, String description) {
        return transactionRepository.saveAndFlush(Transaction.builder()
                .workspace(ctx.workspace())
                .createdByUser(createdBy)
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
                .sourceType(walletUnknown ? TransactionSourceType.EXCEL_MIGRATION : TransactionSourceType.MANUAL)
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

    private SharedDashboardContext sharedContext(String prefix) {
        TestContext ctx = context(prefix, "Asia/Ho_Chi_Minh");
        User member = userRepository.save(User.builder()
                .username(prefix + "_member_" + UUID.randomUUID().toString().substring(0, 8))
                .email(prefix + "_member@example.com")
                .fullName("Shared Member")
                .build());
        User viewer = userRepository.save(User.builder()
                .username(prefix + "_viewer_" + UUID.randomUUID().toString().substring(0, 8))
                .email(prefix + "_viewer@example.com")
                .fullName("Shared Viewer")
                .build());
        workspaceMemberRepository.save(WorkspaceMember.builder().workspace(ctx.workspace()).user(member).role(WorkspaceRole.EDITOR).build());
        workspaceMemberRepository.save(WorkspaceMember.builder().workspace(ctx.workspace()).user(viewer).role(WorkspaceRole.VIEWER).build());
        Wallet cash = wallet(ctx, "Cash", WalletType.CASH, true, true, true, "0");
        Wallet bank = wallet(ctx, "Bank", WalletType.BANK, false, true, true, "0");
        Category income = category(ctx, "Income", CategoryType.INCOME, null, false);
        Category expense = category(ctx, "Expense", CategoryType.EXPENSE, jar(ctx, "NEC", "Needs", "55"), false);

        tx(ctx, ctx.user(), cash, income, TransactionType.INCOME, TransactionStatus.POSTED, "1000", "2026-06-01", "09:00", false, false, "Owner income");
        tx(ctx, ctx.user(), cash, expense, TransactionType.EXPENSE, TransactionStatus.POSTED, "300", "2026-06-02", "09:00", false, false, "Owner expense");
        tx(ctx, member, cash, income, TransactionType.INCOME, TransactionStatus.POSTED, "2000", "2026-06-03", "09:00", false, false, "Member income");
        tx(ctx, member, cash, expense, TransactionType.EXPENSE, TransactionStatus.POSTED, "700", "2026-06-04", "09:00", false, false, "Member expense");
        tx(ctx, ctx.user(), cash, expense, TransactionType.EXPENSE, TransactionStatus.POSTED, "99", "2026-06-05", "09:00", true, false, "Deleted expense");
        transfer(ctx, bank, cash, "500", "2026-06-06");
        return new SharedDashboardContext(ctx.user(), member, viewer, ctx.workspace());
    }

    private record TestContext(User user, Workspace workspace) {}
    private record SharedDashboardContext(User owner, User member, User viewer, Workspace workspace) {}
}
