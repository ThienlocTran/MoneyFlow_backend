package com.moneyflowbackend;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.common.model.SpendingScope;
import com.moneyflowbackend.transaction.model.Transaction;
import com.moneyflowbackend.transaction.model.TransactionStatus;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.model.WalletType;
import com.moneyflowbackend.wallet.repository.WalletRepository;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SpendingScopePersistenceIntegrationTests {
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired EntityManager entityManager;

    @Test
    void enumContainsOnlyLockedValues() {
        assertThat(SpendingScope.values()).containsExactly(
                SpendingScope.PERSONAL,
                SpendingScope.FAMILY,
                SpendingScope.SHARED,
                SpendingScope.WORK,
                SpendingScope.OTHER);
    }

    @Test
    void expenseTransactionsPersistAndLoadEveryScopeAndNull() {
        TestContext ctx = createContext("scope_tx");
        Wallet cash = wallet(ctx);
        Category expense = category(ctx, "Food", CategoryType.EXPENSE, null);

        for (SpendingScope scope : SpendingScope.values()) {
            Transaction saved = transactionRepository.saveAndFlush(transaction(ctx, cash, expense, scope));
            entityManager.clear();

            assertThat(transactionRepository.findById(saved.getId()).orElseThrow().getSpendingScope()).isEqualTo(scope);
        }

        Transaction nullScope = transactionRepository.saveAndFlush(transaction(ctx, cash, expense, null));
        entityManager.clear();

        assertThat(transactionRepository.findById(nullScope.getId()).orElseThrow().getSpendingScope()).isNull();
    }

    @Test
    void expenseCategoriesPersistAndLoadDefaultScopeAndNull() {
        TestContext ctx = createContext("scope_category");

        Category work = category(ctx, "Fuel", CategoryType.EXPENSE, SpendingScope.WORK);
        Category nullScope = category(ctx, "Other", CategoryType.EXPENSE, null);
        entityManager.clear();

        assertThat(categoryRepository.findById(work.getId()).orElseThrow().getDefaultSpendingScope()).isEqualTo(SpendingScope.WORK);
        assertThat(categoryRepository.findById(nullScope.getId()).orElseThrow().getDefaultSpendingScope()).isNull();
    }

    @Test
    void historicalNullRowsStillLoad() {
        TestContext ctx = createContext("scope_history");
        Wallet cash = wallet(ctx);
        Category expense = category(ctx, "Legacy", CategoryType.EXPENSE, null);

        Transaction historical = transaction(ctx, cash, expense, null);
        historical.setHistorical(true);
        Transaction saved = transactionRepository.saveAndFlush(historical);
        entityManager.clear();

        Transaction loaded = transactionRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.isHistorical()).isTrue();
        assertThat(loaded.getSpendingScope()).isNull();
        assertThat(categoryRepository.findById(expense.getId()).orElseThrow().getDefaultSpendingScope()).isNull();
    }

    private TestContext createContext(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .fullName("Spending Scope Test User")
                .build());
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .name(prefix + " workspace")
                .createdByUser(user)
                .build());
        workspaceMemberRepository.save(WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(WorkspaceRole.OWNER)
                .build());
        return new TestContext(user, workspace);
    }

    private Wallet wallet(TestContext ctx) {
        return walletRepository.saveAndFlush(Wallet.builder()
                .workspace(ctx.workspace())
                .name("Cash")
                .walletType(WalletType.CASH)
                .openingBalance(BigDecimal.ZERO)
                .isActive(true)
                .includeInTotal(true)
                .build());
    }

    private Category category(TestContext ctx, String name, CategoryType type, SpendingScope scope) {
        return categoryRepository.saveAndFlush(Category.builder()
                .workspace(ctx.workspace())
                .name(name)
                .categoryType(type)
                .defaultSpendingScope(scope)
                .isActive(true)
                .build());
    }

    private Transaction transaction(TestContext ctx, Wallet wallet, Category category, SpendingScope scope) {
        return Transaction.builder()
                .workspace(ctx.workspace())
                .createdByUser(ctx.user())
                .wallet(wallet)
                .category(category)
                .transactionType(TransactionType.EXPENSE)
                .transactionStatus(TransactionStatus.POSTED)
                .amount(BigDecimal.TEN)
                .transactionDate(LocalDate.of(2026, 7, 21))
                .spendingScope(scope)
                .build();
    }

    private record TestContext(User user, Workspace workspace) {
    }
}
