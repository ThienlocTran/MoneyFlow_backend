package com.moneyflowbackend;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryKeyword;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.category.repository.CategoryKeywordRepository;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.income.model.IncomeSource;
import com.moneyflowbackend.income.model.IncomeSourceStatus;
import com.moneyflowbackend.income.model.IncomeSourceType;
import com.moneyflowbackend.income.repository.IncomeSourceRepository;
import com.moneyflowbackend.suggestion.dto.QuickEntrySuggestionRequest;
import com.moneyflowbackend.suggestion.dto.QuickEntrySuggestionResponse;
import com.moneyflowbackend.suggestion.dto.SuggestionItemResponse;
import com.moneyflowbackend.suggestion.dto.SuggestionSource;
import com.moneyflowbackend.suggestion.dto.SuggestionTargetType;
import com.moneyflowbackend.suggestion.service.QuickEntrySuggestionService;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class QuickEntrySuggestionIntegrationTests {
    @Autowired QuickEntrySuggestionService suggestionService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired CategoryKeywordRepository categoryKeywordRepository;
    @Autowired IncomeSourceRepository incomeSourceRepository;
    @Autowired TransactionRepository transactionRepository;

    @Test
    void keywordMatchSuggestsExpenseCategoryWithoutWriting() {
        TestContext ctx = context("sg_keyword");
        Category fuel = category(ctx, "Xăng xe", CategoryType.EXPENSE);
        category(ctx, "Ăn uống", CategoryType.EXPENSE);
        keyword(ctx, fuel, "xăng", 3);
        long transactionCount = transactionRepository.count();
        long categoryCount = categoryRepository.count();

        QuickEntrySuggestionResponse response = suggestionService.suggest(
                ctx.workspace().getId(), request("đổ xăng 50", "TRANSACTION_EXPENSE", "50000"), ctx.user().getId());

        assertThat(response.getCategorySuggestions()).isNotEmpty();
        assertThat(response.getCategorySuggestions().getFirst()).satisfies(item -> {
            assertThat(item.getId()).isEqualTo(fuel.getId());
            assertThat(item.getName()).isEqualTo("Xăng xe");
            assertThat(item.getType()).isEqualTo(SuggestionTargetType.CATEGORY);
            assertThat(item.getSource()).isEqualTo(SuggestionSource.KEYWORD);
            assertThat(item.getConfidence()).isGreaterThan(0.8d);
            assertThat(item.isLowConfidence()).isFalse();
            assertThat(item.getReason()).contains("xăng");
        });
        // Nothing is persisted by a suggestion call.
        assertThat(transactionRepository.count()).isEqualTo(transactionCount);
        assertThat(categoryRepository.count()).isEqualTo(categoryCount);
    }

    @Test
    void matchingIsDiacriticInsensitiveOnNames() {
        TestContext ctx = context("sg_diacritic");
        Category fuel = category(ctx, "Xăng xe", CategoryType.EXPENSE);

        QuickEntrySuggestionResponse response = suggestionService.suggest(
                ctx.workspace().getId(), request("do xang xe 50", "TRANSACTION_EXPENSE", "50000"), ctx.user().getId());

        assertThat(response.getCategorySuggestions())
                .anySatisfy(item -> {
                    assertThat(item.getId()).isEqualTo(fuel.getId());
                    assertThat(item.getSource()).isEqualTo(SuggestionSource.EXACT_NAME);
                });
    }

    @Test
    void historySuggestsCategoryAndWalletUsedForSimilarText() {
        TestContext ctx = context("sg_history");
        Wallet cash = wallet(ctx, "Tiền mặt", false);
        wallet(ctx, "Cake", false);
        Category fuel = category(ctx, "Nhiên liệu", CategoryType.EXPENSE);
        // No keyword, no name overlap: only history can explain the suggestion.
        transaction(ctx, cash, fuel, TransactionType.EXPENSE, "50000", "đổ xăng buổi sáng");
        transaction(ctx, cash, fuel, TransactionType.EXPENSE, "60000", "đổ xăng đi làm");

        QuickEntrySuggestionResponse response = suggestionService.suggest(
                ctx.workspace().getId(), request("đổ xăng 50", "TRANSACTION_EXPENSE", "50000"), ctx.user().getId());

        assertThat(response.getCategorySuggestions())
                .anySatisfy(item -> {
                    assertThat(item.getId()).isEqualTo(fuel.getId());
                    assertThat(item.getSource()).isEqualTo(SuggestionSource.HISTORY);
                    assertThat(item.getReason()).contains("2 lần");
                });
        assertThat(response.getWalletSuggestions())
                .anySatisfy(item -> {
                    assertThat(item.getId()).isEqualTo(cash.getId());
                    assertThat(item.getType()).isEqualTo(SuggestionTargetType.WALLET);
                });
    }

    @Test
    void incomeIntentSuggestsIncomeSourcesAndNoExpenseCategories() {
        TestContext ctx = context("sg_income");
        category(ctx, "Ăn uống", CategoryType.EXPENSE);
        IncomeSource salary = incomeSource(ctx, "Lương công ty", IncomeSourceStatus.ACTIVE);

        QuickEntrySuggestionResponse response = suggestionService.suggest(
                ctx.workspace().getId(), request("nhận lương công ty 12 triệu", "TRANSACTION_INCOME", "12000000"), ctx.user().getId());

        assertThat(response.getCategorySuggestions()).isEmpty();
        assertThat(response.getIncomeSourceSuggestions())
                .anySatisfy(item -> {
                    assertThat(item.getId()).isEqualTo(salary.getId());
                    assertThat(item.getType()).isEqualTo(SuggestionTargetType.INCOME_SOURCE);
                    assertThat(item.getSource()).isEqualTo(SuggestionSource.EXACT_NAME);
                });
    }

    @Test
    void inactiveAndArchivedRecordsAreNeverSuggested() {
        TestContext ctx = context("sg_inactive");
        Category archived = category(ctx, "Xăng xe", CategoryType.EXPENSE);
        archived.setArchived(true);
        categoryRepository.saveAndFlush(archived);
        Category inactive = category(ctx, "Xăng dầu", CategoryType.EXPENSE);
        inactive.setActive(false);
        categoryRepository.saveAndFlush(inactive);
        Wallet inactiveWallet = wallet(ctx, "Ví cũ", false);
        inactiveWallet.setActive(false);
        walletRepository.saveAndFlush(inactiveWallet);
        IncomeSource archivedSource = incomeSource(ctx, "Lương cũ", IncomeSourceStatus.ARCHIVED);

        QuickEntrySuggestionResponse expense = suggestionService.suggest(
                ctx.workspace().getId(), request("đổ xăng 50", "TRANSACTION_EXPENSE", "50000"), ctx.user().getId());
        QuickEntrySuggestionResponse income = suggestionService.suggest(
                ctx.workspace().getId(), request("nhận lương cũ 5 triệu", "TRANSACTION_INCOME", "5000000"), ctx.user().getId());

        assertThat(expense.getCategorySuggestions()).noneMatch(item -> item.getId().equals(archived.getId()));
        assertThat(expense.getCategorySuggestions()).noneMatch(item -> item.getId().equals(inactive.getId()));
        assertThat(expense.getWalletSuggestions()).noneMatch(item -> item.getId().equals(inactiveWallet.getId()));
        assertThat(income.getIncomeSourceSuggestions()).noneMatch(item -> item.getId().equals(archivedSource.getId()));
    }

    @Test
    void suggestionsStayInsideTheirWorkspaceAndRequireMembership() {
        TestContext owner = context("sg_owner");
        TestContext other = context("sg_other");
        Category ownCategory = category(owner, "Xăng xe", CategoryType.EXPENSE);
        Category foreignCategory = category(other, "Xăng xe ngoài", CategoryType.EXPENSE);
        wallet(other, "Ví ngoài", false);

        QuickEntrySuggestionResponse response = suggestionService.suggest(
                owner.workspace().getId(), request("đổ xăng 50", "TRANSACTION_EXPENSE", "50000"), owner.user().getId());

        assertThat(response.getCategorySuggestions()).allMatch(item -> item.getId().equals(ownCategory.getId()));
        assertThat(response.getCategorySuggestions()).noneMatch(item -> item.getId().equals(foreignCategory.getId()));
        assertThat(response.getWalletSuggestions()).isEmpty();
        assertThatThrownBy(() -> suggestionService.suggest(
                owner.workspace().getId(), request("đổ xăng 50", "TRANSACTION_EXPENSE", "50000"), other.user().getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("FORBIDDEN");
    }

    @Test
    void blankTextIsRejectedAndLowConfidenceIsFlagged() {
        TestContext ctx = context("sg_lowconf");
        wallet(ctx, "Tiền mặt", true);
        category(ctx, "Ăn uống", CategoryType.EXPENSE);

        assertThatThrownBy(() -> suggestionService.suggest(
                ctx.workspace().getId(), request("   ", "TRANSACTION_EXPENSE", "50000"), ctx.user().getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("TEXT_REQUIRED");

        QuickEntrySuggestionResponse response = suggestionService.suggest(
                ctx.workspace().getId(), request("mua vật tư linh kiện", "TRANSACTION_EXPENSE", null), ctx.user().getId());

        assertThat(response.getCategorySuggestions()).isEmpty();
        assertThat(response.getWalletSuggestions()).isNotEmpty();
        assertThat(response.getWalletSuggestions()).allMatch(item -> item.isLowConfidence());
        assertThat(response.getWarnings())
                .anyMatch(warning -> warning.getCode().equals("LOW_CONFIDENCE_ONLY"))
                .anyMatch(warning -> warning.getCode().equals("AMOUNT_MISSING"))
                .anyMatch(warning -> warning.getCode().equals("NO_CATEGORY_MATCH"));
    }

    @Test
    void unsupportedIntentReturnsWarningNotServerError() {
        TestContext ctx = context("sg_intent");
        wallet(ctx, "Tiền mặt", true);
        category(ctx, "Ăn uống", CategoryType.EXPENSE);

        QuickEntrySuggestionResponse response = suggestionService.suggest(
                ctx.workspace().getId(), request("chuyển khoản linh tinh", "TRANSACTION_LOAN", "50000"), ctx.user().getId());

        // Unknown intents degrade gracefully: a warning, never an exception.
        assertThat(response.getWarnings())
                .anyMatch(warning -> warning.getCode().equals("UNSUPPORTED_INTENT_FOR_SUGGESTIONS"));
        // Only wallet suggestions are offered for an unknown intent; no category / income guesses.
        assertThat(response.getCategorySuggestions()).isEmpty();
        assertThat(response.getIncomeSourceSuggestions()).isEmpty();
    }

    @Test
    void responseArraysAreNeverNullAndConfidenceStaysBounded() {
        TestContext ctx = context("sg_contract");
        Wallet cash = wallet(ctx, "Tiền mặt", true);
        Category fuel = category(ctx, "Xăng xe", CategoryType.EXPENSE);
        keyword(ctx, fuel, "xăng", 3);
        transaction(ctx, cash, fuel, TransactionType.EXPENSE, "50000", "đổ xăng đi làm");

        QuickEntrySuggestionResponse response = suggestionService.suggest(
                ctx.workspace().getId(), request("đổ xăng 50", "TRANSACTION_EXPENSE", "50000"), ctx.user().getId());

        // Contract: every collection is present, never null, so the frontend can iterate safely.
        assertThat(response.getCategorySuggestions()).isNotNull();
        assertThat(response.getWalletSuggestions()).isNotNull();
        assertThat(response.getIncomeSourceSuggestions()).isNotNull();
        assertThat(response.getWarnings()).isNotNull();

        List<SuggestionItemResponse> all = new ArrayList<>();
        all.addAll(response.getCategorySuggestions());
        all.addAll(response.getWalletSuggestions());
        all.addAll(response.getIncomeSourceSuggestions());
        assertThat(all).isNotEmpty();
        // Confidence is always a probability in [0, 1] and lowConfidence is derived consistently.
        assertThat(all).allSatisfy(item -> {
            assertThat(item.getConfidence()).isBetween(0d, 1d);
            assertThat(item.isLowConfidence()).isEqualTo(item.getConfidence() < 0.6d);
        });
        // No duplicate suggestion ids within a single target list.
        assertThat(response.getCategorySuggestions().stream().map(SuggestionItemResponse::getId).distinct().count())
                .isEqualTo(response.getCategorySuggestions().size());
        assertThat(response.getWalletSuggestions().stream().map(SuggestionItemResponse::getId).distinct().count())
                .isEqualTo(response.getWalletSuggestions().size());
    }

    private QuickEntrySuggestionRequest request(String text, String intentType, String amount) {
        QuickEntrySuggestionRequest req = new QuickEntrySuggestionRequest();
        req.setText(text);
        req.setIntentType(intentType);
        req.setAmount(amount == null ? null : new BigDecimal(amount));
        return req;
    }

    private TestContext context(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.saveAndFlush(User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .fullName("Suggestion Test")
                .build());
        Workspace workspace = workspaceRepository.saveAndFlush(Workspace.builder()
                .name(prefix + " workspace")
                .createdByUser(user)
                .build());
        workspaceMemberRepository.saveAndFlush(WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(WorkspaceRole.OWNER)
                .build());
        return new TestContext(user, workspace);
    }

    private Wallet wallet(TestContext ctx, String name, boolean isDefault) {
        return walletRepository.saveAndFlush(Wallet.builder()
                .workspace(ctx.workspace())
                .name(name)
                .walletType(WalletType.CASH)
                .openingBalance(BigDecimal.ZERO)
                .openingDate(LocalDate.of(2026, 1, 1))
                .isActive(true)
                .isDefault(isDefault)
                .includeInTotal(true)
                .build());
    }

    private Category category(TestContext ctx, String name, CategoryType type) {
        return categoryRepository.saveAndFlush(Category.builder()
                .workspace(ctx.workspace())
                .name(name)
                .categoryType(type)
                .build());
    }

    private CategoryKeyword keyword(TestContext ctx, Category category, String value, int priority) {
        return categoryKeywordRepository.saveAndFlush(CategoryKeyword.builder()
                .workspace(ctx.workspace())
                .category(category)
                .keyword(value)
                .priority(priority)
                .build());
    }

    private IncomeSource incomeSource(TestContext ctx, String name, IncomeSourceStatus status) {
        return incomeSourceRepository.saveAndFlush(IncomeSource.builder()
                .workspace(ctx.workspace())
                .name(name)
                .type(IncomeSourceType.OTHER)
                .status(status)
                .createdByUser(ctx.user())
                .build());
    }

    private Transaction transaction(TestContext ctx, Wallet wallet, Category category, TransactionType type,
                                    String amount, String description) {
        return transactionRepository.saveAndFlush(Transaction.builder()
                .workspace(ctx.workspace())
                .createdByUser(ctx.user())
                .transactionType(type)
                .transactionStatus(TransactionStatus.POSTED)
                .amount(new BigDecimal(amount))
                .currency("VND")
                .transactionDate(LocalDate.of(2026, 7, 20))
                .wallet(wallet)
                .category(category)
                .description(description)
                .build());
    }

    private record TestContext(User user, Workspace workspace) {
    }
}
