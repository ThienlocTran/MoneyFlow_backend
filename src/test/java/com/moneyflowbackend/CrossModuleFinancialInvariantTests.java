package com.moneyflowbackend;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.dashboard.dto.DashboardResponse;
import com.moneyflowbackend.dashboard.service.DashboardService;
import com.moneyflowbackend.transaction.model.Transaction;
import com.moneyflowbackend.transaction.model.TransactionStatus;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.transaction.model.TransferDetail;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.transaction.repository.TransferDetailRepository;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.model.WalletType;
import com.moneyflowbackend.wallet.repository.WalletRepository;
import com.moneyflowbackend.wallet.service.WalletService;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CrossModuleFinancialInvariantTests {
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired TransferDetailRepository transferDetailRepository;
    @Autowired WalletService walletService;
    @Autowired DashboardService dashboardService;

    @Test
    void walletBalancesIncludeMovementsButDashboardIncomeExpenseExcludeDebtTransferPlannedAndDeleted() {
        TestContext ctx = context("invariant");
        Wallet bank = wallet(ctx, "MB Bank", WalletType.BANK, "500");
        Wallet cash = wallet(ctx, "Cash", WalletType.CASH, "1000");
        Category salary = category(ctx, "Salary", CategoryType.INCOME);
        Category food = category(ctx, "Food", CategoryType.EXPENSE);

        tx(ctx, bank, salary, TransactionType.INCOME, TransactionStatus.POSTED, "5000", false);
        tx(ctx, bank, food, TransactionType.EXPENSE, TransactionStatus.POSTED, "800", false);
        tx(ctx, bank, food, TransactionType.EXPENSE, TransactionStatus.PLANNED, "999", false);
        tx(ctx, bank, salary, TransactionType.INCOME, TransactionStatus.POSTED, "111", true);
        transfer(ctx, bank, cash, "1200");
        tx(ctx, bank, null, TransactionType.LOAN_DISBURSEMENT, TransactionStatus.POSTED, "2000", false);
        tx(ctx, bank, null, TransactionType.LOAN_COLLECTION, TransactionStatus.POSTED, "700", false);
        tx(ctx, cash, null, TransactionType.BORROWING_RECEIPT, TransactionStatus.POSTED, "3000", false);
        tx(ctx, cash, null, TransactionType.BORROWING_REPAYMENT, TransactionStatus.POSTED, "1000", false);
        tx(ctx, cash, null, TransactionType.LOAN_COLLECTION, TransactionStatus.PLANNED, "999", false);
        tx(ctx, cash, null, TransactionType.BORROWING_RECEIPT, TransactionStatus.POSTED, "999", true);

        DashboardResponse dashboard = dashboardService.getDashboard(ctx.workspace().getId(), "2026-06", "FULL_MONTH", ctx.user().getId());

        assertThat(walletService.calculateCurrentBalance(bank.getId())).isEqualByComparingTo("2200");
        assertThat(walletService.calculateCurrentBalance(cash.getId())).isEqualByComparingTo("4200");
        assertThat(dashboard.getWalletTotal()).isEqualByComparingTo("6400");
        assertThat(dashboard.getIncome()).isEqualByComparingTo("5000");
        assertThat(dashboard.getExpense()).isEqualByComparingTo("800");
        assertThat(dashboard.getNetCashFlow()).isEqualByComparingTo("4200");
        assertThat(dashboard.getTransactionCount()).isEqualTo(2);
    }

    private TestContext context(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .fullName("Invariant Test")
                .build());
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .name(prefix + " workspace")
                .createdByUser(user)
                .timezone("Asia/Ho_Chi_Minh")
                .build());
        workspaceMemberRepository.save(WorkspaceMember.builder().workspace(workspace).user(user).role(WorkspaceRole.OWNER).build());
        return new TestContext(user, workspace);
    }

    private Wallet wallet(TestContext ctx, String name, WalletType type, String openingBalance) {
        return walletRepository.saveAndFlush(Wallet.builder()
                .workspace(ctx.workspace())
                .name(name)
                .walletType(type)
                .openingBalance(new BigDecimal(openingBalance))
                .openingDate(LocalDate.of(2026, 6, 1))
                .isActive(true)
                .includeInTotal(true)
                .build());
    }

    private Category category(TestContext ctx, String name, CategoryType type) {
        return categoryRepository.saveAndFlush(Category.builder()
                .workspace(ctx.workspace())
                .name(name)
                .categoryType(type)
                .isActive(true)
                .isArchived(false)
                .build());
    }

    private Transaction tx(TestContext ctx, Wallet wallet, Category category, TransactionType type,
                           TransactionStatus status, String amount, boolean deleted) {
        return transactionRepository.saveAndFlush(Transaction.builder()
                .workspace(ctx.workspace())
                .createdByUser(ctx.user())
                .wallet(wallet)
                .category(category)
                .transactionType(type)
                .transactionStatus(status)
                .amount(new BigDecimal(amount))
                .currency("VND")
                .transactionDate(LocalDate.of(2026, 6, 15))
                .walletUnknown(false)
                .deletedAt(deleted ? Instant.now() : null)
                .build());
    }

    private void transfer(TestContext ctx, Wallet source, Wallet destination, String amount) {
        Transaction tx = tx(ctx, source, null, TransactionType.TRANSFER, TransactionStatus.POSTED, amount, false);
        transferDetailRepository.saveAndFlush(TransferDetail.builder()
                .transactionId(tx.getId())
                .transaction(tx)
                .sourceWallet(source)
                .destinationWallet(destination)
                .build());
    }

    private record TestContext(User user, Workspace workspace) {}
}
