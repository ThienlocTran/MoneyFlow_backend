package com.moneyflowbackend;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryKeyword;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.category.repository.CategoryKeywordRepository;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.quickentry.dto.QuickEntryButtonRequest;
import com.moneyflowbackend.quickentry.dto.QuickEntryConfirmRequest;
import com.moneyflowbackend.quickentry.dto.QuickEntryPreviewResponse;
import com.moneyflowbackend.quickentry.service.QuickEntryService;
import com.moneyflowbackend.transaction.model.Transaction;
import com.moneyflowbackend.transaction.audit.TransactionAuditLogRepository;
import com.moneyflowbackend.transaction.model.TransactionSourceType;
import com.moneyflowbackend.transaction.model.TransactionStatus;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.voice.repository.VoiceRecordRepository;
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
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class QuickEntryModuleIntegrationTests {
    @Autowired QuickEntryService quickEntryService;
    @Autowired WalletService walletService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired CategoryKeywordRepository keywordRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired TransactionAuditLogRepository transactionAuditLogRepository;
    @Autowired VoiceRecordRepository voiceRecordRepository;

    @Test
    void parseDoesNotPersistAndConfirmExpenseStoresQuickTextRawInputAndBalance() {
        TestContext ctx = createContext("qe_expense", WorkspaceRole.OWNER);
        Wallet cash = wallet(ctx, "Tien mat", WalletType.CASH, true, "0");
        Category food = category(ctx, "Food", CategoryType.EXPENSE, true, false, false);
        keyword(ctx, food, "an sang", 10);
        long before = transactionRepository.count();

        QuickEntryPreviewResponse preview = quickEntryService.parse(ctx.workspace().getId(), "an sang 35k tien mat", ctx.user().getId());

        assertThat(transactionRepository.count()).isEqualTo(before);
        assertThat(preview.isReadyToConfirm()).isTrue();
        assertThat(preview.getAmount()).isEqualByComparingTo("35000");
        assertThat(preview.getWalletId()).isEqualTo(cash.getId());
        assertThat(preview.getCategoryId()).isEqualTo(food.getId());

        var saved = quickEntryService.confirm(ctx.workspace().getId(), confirm(preview), ctx.user().getId());
        Transaction tx = transactionRepository.findById(saved.getId()).orElseThrow();
        assertThat(tx.getSourceType()).isEqualTo(TransactionSourceType.QUICK_TEXT);
        assertThat(tx.getRawInput()).isEqualTo("an sang 35k tien mat");
        assertThat(tx.getVoiceRecordId()).isNull();
        var logs = transactionAuditLogRepository.findByWorkspaceIdAndTransactionIdOrderByCreatedAtAsc(ctx.workspace().getId(), saved.getId());
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getAction().name()).isEqualTo("CREATE");
        assertThat(logs.get(0).getAfterData()).containsEntry("sourceType", "QUICK_TEXT");
        assertThat(walletService.calculateCurrentBalance(cash.getId())).isEqualByComparingTo("-35000");
    }

    @Test
    void incomePlannedTransferAndQuickButtonUseTransactionServiceValidation() {
        TestContext ctx = createContext("qe_flow", WorkspaceRole.OWNER);
        Wallet cash = wallet(ctx, "Tien mat", WalletType.CASH, true, "0");
        Wallet bank = wallet(ctx, "MB Bank", WalletType.BANK, false, "0");
        Category salary = category(ctx, "Salary", CategoryType.INCOME, true, false, false);
        Category rent = category(ctx, "Rent", CategoryType.EXPENSE, true, false, true);
        keyword(ctx, salary, "luong", 10);
        keyword(ctx, rent, "dong tien tro", 10);

        var incomePreview = quickEntryService.parse(ctx.workspace().getId(), "luong 5 trieu mb", ctx.user().getId());
        var income = quickEntryService.confirm(ctx.workspace().getId(), confirm(incomePreview), ctx.user().getId());
        assertThat(transactionRepository.findById(income.getId()).orElseThrow().getSourceType()).isEqualTo(TransactionSourceType.QUICK_TEXT);
        assertThat(walletService.calculateCurrentBalance(bank.getId())).isEqualByComparingTo("5000000");

        var plannedPreview = quickEntryService.parse(ctx.workspace().getId(), "mai dong tien tro 2tr", ctx.user().getId());
        assertThat(plannedPreview.getStatus()).isEqualTo(TransactionStatus.PLANNED);
        var planned = quickEntryService.confirm(ctx.workspace().getId(), confirm(plannedPreview), ctx.user().getId());
        assertThat(transactionRepository.findById(planned.getId()).orElseThrow().getTransactionStatus()).isEqualTo(TransactionStatus.PLANNED);
        assertThat(walletService.calculateCurrentBalance(cash.getId())).isEqualByComparingTo("0");

        var transferPreview = quickEntryService.parse(ctx.workspace().getId(), "chuyen 1 trieu tu MB Bank sang Tien mat", ctx.user().getId());
        var transfer = quickEntryService.confirm(ctx.workspace().getId(), confirm(transferPreview), ctx.user().getId());
        assertThat(transactionRepository.findById(transfer.getId()).orElseThrow().getTransactionType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(walletService.calculateCurrentBalance(bank.getId())).isEqualByComparingTo("4000000");
        assertThat(walletService.calculateCurrentBalance(cash.getId())).isEqualByComparingTo("1000000");

        QuickEntryButtonRequest button = new QuickEntryButtonRequest();
        button.setCategoryId(rent.getId());
        button.setAmount(new BigDecimal("50000"));
        button.setTransactionDate(LocalDate.now());
        button.setDescription("Rent quick");
        var buttonTx = quickEntryService.button(ctx.workspace().getId(), button, ctx.user().getId());
        assertThat(transactionRepository.findById(buttonTx.getId()).orElseThrow().getSourceType()).isEqualTo(TransactionSourceType.QUICK_BUTTON);
        assertThat(walletService.calculateCurrentBalance(cash.getId())).isEqualByComparingTo("950000");
    }

    @Test
    void voiceConfirmStoresVoiceSourceAndTranscriptRawInput() {
        TestContext ctx = createContext("qe_voice", WorkspaceRole.OWNER);
        Wallet cash = wallet(ctx, "Tien mat", WalletType.CASH, true, "0");
        Category food = category(ctx, "Food", CategoryType.EXPENSE, true, false, false);
        keyword(ctx, food, "an sang", 10);

        QuickEntryPreviewResponse preview = quickEntryService.parse(ctx.workspace().getId(), "an sang 35k tien mat", ctx.user().getId());
        var saved = quickEntryService.confirmVoice(ctx.workspace().getId(), confirm(preview), ctx.user().getId());

        Transaction tx = transactionRepository.findById(saved.getId()).orElseThrow();
        assertThat(tx.getSourceType()).isEqualTo(TransactionSourceType.VOICE);
        assertThat(tx.getRawInput()).isEqualTo("an sang 35k tien mat");
        assertThat(tx.getVoiceRecordId()).isNotNull();
        var voiceRecord = voiceRecordRepository.findById(tx.getVoiceRecordId()).orElseThrow();
        assertThat(voiceRecord.getWorkspace().getId()).isEqualTo(ctx.workspace().getId());
        assertThat(voiceRecord.getCreatedByUser().getId()).isEqualTo(ctx.user().getId());
        assertThat(voiceRecord.getOriginalTranscript()).isEqualTo("an sang 35k tien mat");
        assertThat(voiceRecord.getEditedTranscript()).isEqualTo("an sang 35k tien mat");
        assertThat(voiceRecord.getAudioUrl()).isNull();
        var logs = transactionAuditLogRepository.findByWorkspaceIdAndTransactionIdOrderByCreatedAtAsc(ctx.workspace().getId(), saved.getId());
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getAfterData()).containsEntry("sourceType", "VOICE");
        assertThat(logs.get(0).getAfterData()).containsEntry("voiceRecordId", tx.getVoiceRecordId());
        assertThat(logs.get(0).getAfterData()).doesNotContainKeys("audioUrl", "storagePublicId");
        assertThat(walletService.calculateCurrentBalance(cash.getId())).isEqualByComparingTo("-35000");
    }

    @Test
    void parseSuggestsRecentActiveWalletBeforeDefaultAndIgnoresInactiveWallet() {
        TestContext ctx = createContext("qe_wallet_suggest", WorkspaceRole.OWNER);
        Wallet cash = wallet(ctx, "Tien mat", WalletType.CASH, true, "0");
        Wallet bank = wallet(ctx, "MB Bank", WalletType.BANK, false, "0");
        Category food = category(ctx, "Food", CategoryType.EXPENSE, true, false, false);
        keyword(ctx, food, "an sang", 10);

        var explicitBank = quickEntryService.parse(ctx.workspace().getId(), "an sang 40k mb", ctx.user().getId());
        quickEntryService.confirm(ctx.workspace().getId(), confirm(explicitBank), ctx.user().getId());

        var suggested = quickEntryService.parse(ctx.workspace().getId(), "an sang 35k", ctx.user().getId());
        assertThat(suggested.getWalletId()).isEqualTo(bank.getId());
        assertThat(suggested.getWarnings()).contains("SUGGESTED_WALLET_USED");

        bank.setActive(false);
        walletRepository.saveAndFlush(bank);

        var fallback = quickEntryService.parse(ctx.workspace().getId(), "an sang 35k", ctx.user().getId());
        assertThat(fallback.getWalletId()).isEqualTo(cash.getId());
        assertThat(fallback.getWarnings()).contains("DEFAULT_WALLET_USED");
    }

    @Test
    void voiceConfirmValidationStillRejectsMissingWalletInactiveWalletAndCategoryMismatch() {
        TestContext ctx = createContext("qe_voice_rules", WorkspaceRole.OWNER);
        Wallet cash = wallet(ctx, "Tien mat", WalletType.CASH, true, "0");
        Wallet inactive = wallet(ctx, "Inactive", WalletType.CASH, false, "0");
        inactive.setActive(false);
        walletRepository.saveAndFlush(inactive);
        Category food = category(ctx, "Food", CategoryType.EXPENSE, true, false, false);
        Category income = category(ctx, "Income", CategoryType.INCOME, true, false, false);
        keyword(ctx, food, "an sang", 10);

        QuickEntryPreviewResponse preview = quickEntryService.parse(ctx.workspace().getId(), "an sang 35k tien mat", ctx.user().getId());
        QuickEntryConfirmRequest missingWallet = confirm(preview);
        missingWallet.setWalletId(null);
        assertBusinessCode(() -> quickEntryService.confirmVoice(ctx.workspace().getId(), missingWallet, ctx.user().getId()), "WALLET_NOT_FOUND");

        QuickEntryConfirmRequest inactiveWallet = confirm(preview);
        inactiveWallet.setWalletId(inactive.getId());
        assertBusinessCode(() -> quickEntryService.confirmVoice(ctx.workspace().getId(), inactiveWallet, ctx.user().getId()), "WALLET_INACTIVE");

        QuickEntryConfirmRequest mismatch = confirm(preview);
        mismatch.setCategoryId(income.getId());
        mismatch.setWalletId(cash.getId());
        mismatch.setType(TransactionType.EXPENSE);
        assertBusinessCode(() -> quickEntryService.confirmVoice(ctx.workspace().getId(), mismatch, ctx.user().getId()), "CATEGORY_TYPE_MISMATCH");
    }

    @Test
    void quickButtonAndAuthorizationRulesRejectInvalidAccess() {
        TestContext owner = createContext("qe_owner", WorkspaceRole.OWNER);
        TestContext viewer = createContext("qe_viewer", WorkspaceRole.VIEWER);
        TestContext outsider = createContext("qe_outsider", WorkspaceRole.OWNER);
        workspaceMemberRepository.save(WorkspaceMember.builder().workspace(owner.workspace()).user(viewer.user()).role(WorkspaceRole.VIEWER).build());
        wallet(owner, "Cash", WalletType.CASH, true, "0");
        Category normal = category(owner, "Normal", CategoryType.EXPENSE, true, false, false);
        Category quick = category(owner, "Quick", CategoryType.EXPENSE, true, false, true);
        keyword(owner, quick, "quick", 1);

        assertThat(quickEntryService.parse(owner.workspace().getId(), "quick 35k", viewer.user().getId()).getAmount()).isEqualByComparingTo("35000");
        assertBusinessCode(() -> quickEntryService.confirm(owner.workspace().getId(), confirm(quickEntryService.parse(owner.workspace().getId(), "quick 35k", owner.user().getId())), viewer.user().getId()), "FORBIDDEN");
        assertBusinessCode(() -> quickEntryService.parse(owner.workspace().getId(), "quick 35k", outsider.user().getId()), "WORKSPACE_ACCESS_DENIED");

        QuickEntryButtonRequest req = new QuickEntryButtonRequest();
        req.setCategoryId(normal.getId());
        req.setAmount(BigDecimal.ONE);
        assertBusinessCode(() -> quickEntryService.button(owner.workspace().getId(), req, owner.user().getId()), "CATEGORY_NOT_QUICK_ACTION");
    }

    private QuickEntryConfirmRequest confirm(QuickEntryPreviewResponse preview) {
        QuickEntryConfirmRequest req = new QuickEntryConfirmRequest();
        req.setRawInput(preview.getRawInput());
        req.setType(preview.getType());
        req.setStatus(preview.getStatus());
        req.setAmount(preview.getAmount());
        req.setWalletId(preview.getWalletId());
        req.setCategoryId(preview.getCategoryId());
        req.setSourceWalletId(preview.getSourceWalletId());
        req.setDestinationWalletId(preview.getDestinationWalletId());
        req.setTransactionDate(preview.getTransactionDate());
        req.setTransactionTime(preview.getTransactionTime());
        req.setDescription(preview.getDescription());
        req.setNote(preview.getNote());
        return req;
    }

    private TestContext createContext(String prefix, WorkspaceRole role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .fullName("Quick Entry Test User")
                .build());
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .name(prefix + " workspace")
                .createdByUser(user)
                .timezone("Asia/Ho_Chi_Minh")
                .quickAmountUnit("THOUSAND")
                .build());
        workspaceMemberRepository.save(WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(role)
                .build());
        return new TestContext(user, workspace);
    }

    private Wallet wallet(TestContext ctx, String name, WalletType type, boolean isDefault, String openingBalance) {
        return walletRepository.saveAndFlush(Wallet.builder()
                .workspace(ctx.workspace())
                .name(name)
                .walletType(type)
                .openingBalance(new BigDecimal(openingBalance))
                .isDefault(isDefault)
                .isActive(true)
                .includeInTotal(true)
                .build());
    }

    private Category category(TestContext ctx, String name, CategoryType type, boolean active, boolean archived, boolean quickAction) {
        return categoryRepository.saveAndFlush(Category.builder()
                .workspace(ctx.workspace())
                .name(name)
                .categoryType(type)
                .isActive(active)
                .isArchived(archived)
                .isQuickAction(quickAction)
                .build());
    }

    private CategoryKeyword keyword(TestContext ctx, Category category, String value, int priority) {
        return keywordRepository.saveAndFlush(CategoryKeyword.builder()
                .workspace(ctx.workspace())
                .category(category)
                .keyword(value)
                .priority(priority)
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

    private record TestContext(User user, Workspace workspace) {
    }
}
