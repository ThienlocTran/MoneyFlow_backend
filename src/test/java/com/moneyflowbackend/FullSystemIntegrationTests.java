package com.moneyflowbackend;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryKeyword;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.category.repository.CategoryKeywordRepository;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.dashboard.dto.DashboardResponse;
import com.moneyflowbackend.dashboard.service.DashboardService;
import com.moneyflowbackend.quickentry.dto.QuickEntryConfirmRequest;
import com.moneyflowbackend.quickentry.dto.QuickEntryPreviewResponse;
import com.moneyflowbackend.quickentry.service.QuickEntryService;
import com.moneyflowbackend.transaction.dto.TransactionRequest;
import com.moneyflowbackend.transaction.dto.TransactionResponse;
import com.moneyflowbackend.transaction.model.Transaction;
import com.moneyflowbackend.transaction.model.TransactionSourceType;
import com.moneyflowbackend.transaction.model.TransactionStatus;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.transaction.service.TransactionService;
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
import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FullSystemIntegrationTests {
    @Autowired QuickEntryService quickEntryService;
    @Autowired TransactionService transactionService;
    @Autowired WalletService walletService;
    @Autowired DashboardService dashboardService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired CategoryKeywordRepository keywordRepository;
    @Autowired TransactionRepository transactionRepository;

    @Test
    void quickEntryTransactionWalletDashboardAndRestoreStayConsistent() {
        TestContext ctx = context("full_flow");
        Wallet cash = wallet(ctx, "Tien mat", WalletType.CASH, true, true);
        Wallet inactiveWallet = wallet(ctx, "Old cash", WalletType.CASH, false, false);
        Category food = category(ctx, "Food", CategoryType.EXPENSE, true, false);
        Category inactiveCategory = category(ctx, "Old food", CategoryType.EXPENSE, false, false);
        keyword(ctx, food, "an sang");
        long before = transactionRepository.count();

        QuickEntryPreviewResponse preview = quickEntryService.parse(ctx.workspace().getId(), "an sang 35k tien mat", ctx.user().getId());

        assertThat(transactionRepository.count()).isEqualTo(before);
        assertThat(preview.isReadyToConfirm()).isTrue();
        assertThat(preview.getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(preview.getAmount()).isEqualByComparingTo("35000");
        assertThat(preview.getWalletId()).isEqualTo(cash.getId());
        assertThat(preview.getCategoryId()).isEqualTo(food.getId());

        TransactionResponse created = quickEntryService.confirm(ctx.workspace().getId(), confirm(preview), ctx.user().getId());
        Transaction tx = transactionRepository.findById(created.getId()).orElseThrow();
        String month = YearMonth.from(tx.getTransactionDate()).toString();

        assertThat(tx.getSourceType()).isEqualTo(TransactionSourceType.QUICK_TEXT);
        assertThat(tx.getRawInput()).isEqualTo("an sang 35k tien mat");
        assertMoney(cash, month, "-35000", "35000");

        transactionService.update(ctx.workspace().getId(), created.getId(), expenseReq("45000", cash, food, tx), ctx.user().getId());
        assertMoney(cash, month, "-45000", "45000");

        transactionService.delete(ctx.workspace().getId(), created.getId(), ctx.user().getId());
        assertMoney(cash, month, "0", "0");

        transactionService.restore(ctx.workspace().getId(), created.getId(), ctx.user().getId());
        assertMoney(cash, month, "-45000", "45000");

        assertBusinessCode(() -> transactionService.create(ctx.workspace().getId(), expenseReq("1000", inactiveWallet, food, tx), ctx.user().getId()), "WALLET_INACTIVE");
        assertBusinessCode(() -> transactionService.create(ctx.workspace().getId(), expenseReq("1000", cash, inactiveCategory, tx), ctx.user().getId()), "CATEGORY_INACTIVE");
    }

    private void assertMoney(Wallet wallet, String month, String balance, String dashboardExpense) {
        DashboardResponse dashboard = dashboardService.getDashboard(wallet.getWorkspace().getId(), month, "FULL_MONTH", wallet.getWorkspace().getCreatedByUser().getId());
        assertThat(walletService.calculateCurrentBalance(wallet.getId())).isEqualByComparingTo(balance);
        assertThat(dashboard.getExpense()).isEqualByComparingTo(dashboardExpense);
    }

    private QuickEntryConfirmRequest confirm(QuickEntryPreviewResponse preview) {
        QuickEntryConfirmRequest req = new QuickEntryConfirmRequest();
        req.setRawInput(preview.getRawInput());
        req.setType(preview.getType());
        req.setStatus(preview.getStatus());
        req.setAmount(preview.getAmount());
        req.setWalletId(preview.getWalletId());
        req.setCategoryId(preview.getCategoryId());
        req.setTransactionDate(preview.getTransactionDate());
        req.setTransactionTime(preview.getTransactionTime());
        req.setDescription(preview.getDescription());
        req.setNote(preview.getNote());
        return req;
    }

    private TransactionRequest expenseReq(String amount, Wallet wallet, Category category, Transaction tx) {
        TransactionRequest req = new TransactionRequest();
        req.setType(TransactionType.EXPENSE);
        req.setStatus(TransactionStatus.POSTED);
        req.setAmount(new BigDecimal(amount));
        req.setWalletId(wallet.getId());
        req.setCategoryId(category.getId());
        req.setTransactionDate(tx.getTransactionDate());
        req.setTransactionTime(tx.getTransactionTime());
        req.setDescription(tx.getDescription());
        return req;
    }

    private TestContext context(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .fullName("Full System Test")
                .build());
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .name(prefix + " workspace")
                .createdByUser(user)
                .timezone("Asia/Ho_Chi_Minh")
                .quickAmountUnit("THOUSAND")
                .build());
        workspaceMemberRepository.save(WorkspaceMember.builder().workspace(workspace).user(user).role(WorkspaceRole.OWNER).build());
        return new TestContext(user, workspace);
    }

    private Wallet wallet(TestContext ctx, String name, WalletType type, boolean isDefault, boolean active) {
        return walletRepository.saveAndFlush(Wallet.builder()
                .workspace(ctx.workspace())
                .name(name)
                .walletType(type)
                .openingBalance(BigDecimal.ZERO)
                .isDefault(isDefault)
                .isActive(active)
                .includeInTotal(true)
                .build());
    }

    private Category category(TestContext ctx, String name, CategoryType type, boolean active, boolean archived) {
        return categoryRepository.saveAndFlush(Category.builder()
                .workspace(ctx.workspace())
                .name(name)
                .categoryType(type)
                .isActive(active)
                .isArchived(archived)
                .build());
    }

    private CategoryKeyword keyword(TestContext ctx, Category category, String keyword) {
        return keywordRepository.saveAndFlush(CategoryKeyword.builder()
                .workspace(ctx.workspace())
                .category(category)
                .keyword(keyword)
                .priority(10)
                .build());
    }

    private void assertBusinessCode(ThrowingRunnable runnable, String code) {
        assertThatThrownBy(runnable::run)
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(code);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }

    private record TestContext(User user, Workspace workspace) {}
}
