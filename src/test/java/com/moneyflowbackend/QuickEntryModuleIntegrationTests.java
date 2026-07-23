package com.moneyflowbackend;

import com.moneyflowbackend.auth.dto.LoginRequest;
import com.moneyflowbackend.auth.dto.RegisterRequest;
import com.moneyflowbackend.auth.dto.TokenResponse;
import com.moneyflowbackend.auth.dto.UserResponse;
import com.moneyflowbackend.auth.service.AuthService;
import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryKeyword;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.category.repository.CategoryKeywordRepository;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.common.model.SpendingScope;
import com.moneyflowbackend.quickentry.dto.QuickEntryBatchConfirmRequest;
import com.moneyflowbackend.quickentry.dto.QuickEntryButtonRequest;
import com.moneyflowbackend.quickentry.dto.QuickEntryConfirmRequest;
import com.moneyflowbackend.quickentry.dto.QuickEntryPreviewResponse;
import com.moneyflowbackend.quickentry.dto.VoiceIntentType;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class QuickEntryModuleIntegrationTests {
    @Autowired QuickEntryService quickEntryService;
    @Autowired WalletService walletService;
    @Autowired AuthService authService;
    @Autowired MockMvc mockMvc;
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
    void spendingScopeIsManualOnlyAndConfirmDelegatesToTransactionService() throws Exception {
        TestContext ctx = createContext("qe_scope", WorkspaceRole.OWNER);
        Wallet cash = wallet(ctx, "Tien mat", WalletType.CASH, true, "0");
        Category food = category(ctx, "Food", CategoryType.EXPENSE, true, false, false);
        food.setDefaultSpendingScope(SpendingScope.PERSONAL);
        categoryRepository.saveAndFlush(food);
        Category salary = category(ctx, "Salary", CategoryType.INCOME, true, false, false);
        keyword(ctx, food, "chi phi cong viec", 10);
        keyword(ctx, salary, "luong", 10);

        QuickEntryPreviewResponse preview = quickEntryService.parse(ctx.workspace().getId(), "chi phi cong viec 35k tien mat", ctx.user().getId());
        assertThat(preview.getSpendingScope()).isNull();
        assertThat(quickEntryService.confirm(ctx.workspace().getId(), confirm(preview), ctx.user().getId()).getSpendingScope())
                .isEqualTo(SpendingScope.PERSONAL);

        QuickEntryConfirmRequest work = confirm(preview);
        work.setSpendingScope(SpendingScope.WORK);
        assertThat(quickEntryService.confirm(ctx.workspace().getId(), work, ctx.user().getId()).getSpendingScope())
                .isEqualTo(SpendingScope.WORK);

        QuickEntryConfirmRequest explicitNull = confirm(preview);
        explicitNull.setSpendingScope(null);
        assertThat(quickEntryService.confirm(ctx.workspace().getId(), explicitNull, ctx.user().getId()).getSpendingScope()).isNull();

        QuickEntryPreviewResponse incomePreview = quickEntryService.parse(ctx.workspace().getId(), "luong 5 trieu tien mat", ctx.user().getId());
        assertThat(quickEntryService.confirm(ctx.workspace().getId(), confirm(incomePreview), ctx.user().getId()).getSpendingScope()).isNull();
        QuickEntryConfirmRequest badIncome = confirm(incomePreview);
        badIncome.setSpendingScope(SpendingScope.WORK);
        assertBusinessCode(() -> quickEntryService.confirm(ctx.workspace().getId(), badIncome, ctx.user().getId()), "INVALID_TRANSACTION_SPENDING_SCOPE");

        String username = "qe_scope_http_" + UUID.randomUUID().toString().substring(0, 8);
        UserResponse registered = authService.register(registerRequest(username));
        TokenResponse token = authService.login(loginRequest(username));
        Workspace workspace = workspaceRepository.findAllByUserId(registered.getId()).getFirst();
        mockMvc.perform(post("/api/workspaces/" + workspace.getId() + "/quick-entry/confirm")
                        .contentType("application/json")
                        .content("""
                                {"type":"EXPENSE","amount":1,"transactionDate":"2026-07-21","spendingScope":"NOPE"}
                                """)
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message", not(containsString("SpendingScope"))))
                .andExpect(jsonPath("$.message", not(containsString("constraint"))));
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
    void voiceConfirmIsIdempotentPerWorkspaceUserAndKey() {
        TestContext ctx = createContext("qe_voice_idem", WorkspaceRole.OWNER);
        Wallet cash = wallet(ctx, "Tien mat", WalletType.CASH, true, "0");
        Category food = category(ctx, "Food", CategoryType.EXPENSE, true, false, false);
        keyword(ctx, food, "an sang", 10);

        QuickEntryPreviewResponse preview = quickEntryService.parse(ctx.workspace().getId(), "an sang 35k tien mat", ctx.user().getId());
        QuickEntryConfirmRequest firstReq = confirm(preview);
        firstReq.setIdempotencyKey("voice-confirm-key");
        long txBefore = transactionRepository.count();
        long voiceBefore = voiceRecordRepository.count();

        var first = quickEntryService.confirmVoice(ctx.workspace().getId(), firstReq, ctx.user().getId());
        var retry = quickEntryService.confirmVoice(ctx.workspace().getId(), firstReq, ctx.user().getId());

        assertThat(retry.getId()).isEqualTo(first.getId());
        assertThat(retry.getVoiceRecordId()).isEqualTo(first.getVoiceRecordId());
        assertThat(transactionRepository.count()).isEqualTo(txBefore + 1);
        assertThat(voiceRecordRepository.count()).isEqualTo(voiceBefore + 1);

        QuickEntryConfirmRequest nextReq = confirm(preview);
        nextReq.setIdempotencyKey("voice-confirm-key-2");
        var next = quickEntryService.confirmVoice(ctx.workspace().getId(), nextReq, ctx.user().getId());
        assertThat(next.getId()).isNotEqualTo(first.getId());

        TestContext other = createContext("qe_voice_idem_other", WorkspaceRole.OWNER);
        wallet(other, "Tien mat", WalletType.CASH, true, "0");
        Category otherFood = category(other, "Food", CategoryType.EXPENSE, true, false, false);
        keyword(other, otherFood, "an sang", 10);
        QuickEntryConfirmRequest otherReq = confirm(quickEntryService.parse(other.workspace().getId(), "an sang 35k tien mat", other.user().getId()));
        otherReq.setIdempotencyKey("voice-confirm-key");
        var otherTx = quickEntryService.confirmVoice(other.workspace().getId(), otherReq, other.user().getId());
        assertThat(otherTx.getId()).isNotEqualTo(first.getId());
    }

    @Test
    void voiceConfirmValidationFailureDoesNotStoreIdempotentSuccess() {
        TestContext ctx = createContext("qe_voice_idem_validation", WorkspaceRole.OWNER);
        wallet(ctx, "Tien mat", WalletType.CASH, true, "0");
        Category food = category(ctx, "Food", CategoryType.EXPENSE, true, false, false);
        keyword(ctx, food, "an sang", 10);
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        QuickEntryPreviewResponse preview = quickEntryService.parse(ctx.workspace().getId(), "an sang 35k tien mat", ctx.user().getId());
        QuickEntryConfirmRequest missingWallet = confirm(preview);
        missingWallet.setWalletId(null);
        missingWallet.setIdempotencyKey("voice-validation-key");

        assertBusinessCode(() -> quickEntryService.confirmVoice(ctx.workspace().getId(), missingWallet, ctx.user().getId()), "WALLET_NOT_FOUND");
        TestTransaction.flagForRollback();
        TestTransaction.end();
        TestTransaction.start();
        assertThat(voiceRecordRepository.findVoiceIdempotencyMatch(ctx.workspace().getId(), ctx.user().getId(), "voice-validation-key")).isEmpty();

        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
        QuickEntryConfirmRequest valid = confirm(preview);
        valid.setIdempotencyKey("voice-validation-key");
        assertThat(quickEntryService.confirmVoice(ctx.workspace().getId(), valid, ctx.user().getId()).getVoiceRecordId()).isNotNull();
    }

    @Test
    void voiceBatchConfirmCommitsSelectedEditedCandidatesAndReplaysIdempotency() {
        TestContext ctx = createContext("qe_voice_batch", WorkspaceRole.OWNER);
        Wallet cash = wallet(ctx, "Tien mat", WalletType.CASH, true, "0");
        Category food = category(ctx, "Food", CategoryType.EXPENSE, true, false, false);
        Category drink = category(ctx, "Drink", CategoryType.EXPENSE, true, false, false);
        keyword(ctx, food, "an sang", 10);
        keyword(ctx, drink, "cafe", 10);
        QuickEntryPreviewResponse preview = quickEntryService.parse(ctx.workspace().getId(), "an sang 35k cafe 20k", ctx.user().getId());

        QuickEntryBatchConfirmRequest req = batch("voice-batch-1", preview);
        req.getCandidates().get(0).setAmount(new BigDecimal("36000"));
        req.getCandidates().get(0).setDescription("Edited breakfast");
        req.getCandidates().get(1).setSelected(false);

        var saved = quickEntryService.confirmVoiceBatch(ctx.workspace().getId(), req, ctx.user().getId());
        var replay = quickEntryService.confirmVoiceBatch(ctx.workspace().getId(), req, ctx.user().getId());

        assertThat(saved.isIdempotentReplay()).isFalse();
        assertThat(saved.getCommittedCount()).isEqualTo(1);
        assertThat(replay.isIdempotentReplay()).isTrue();
        assertThat(replay.getCommittedCount()).isEqualTo(1);
        assertThat(transactionRepository.findAll()).filteredOn(tx -> tx.getWorkspace().getId().equals(ctx.workspace().getId())).hasSize(1);
        Transaction tx = transactionRepository.findById(saved.getItems().get(0).getTransaction().getId()).orElseThrow();
        assertThat(tx.getAmount()).isEqualByComparingTo("36000");
        assertThat(tx.getDescription()).isEqualTo("Edited breakfast");
        assertThat(tx.getCategory().getId()).isEqualTo(food.getId());
        assertThat(tx.getWallet().getId()).isEqualTo(cash.getId());
        assertThat(tx.getVoiceRecordId()).isEqualTo(saved.getVoiceRecordId());
        assertThat(walletService.calculateCurrentBalance(cash.getId())).isEqualByComparingTo("-36000");
    }

    @Test
    void voiceBatchConfirmIsAtomicAndRejectsUnsupportedTypes() {
        TestContext ctx = createContext("qe_voice_atomic", WorkspaceRole.OWNER);
        wallet(ctx, "Tien mat", WalletType.CASH, true, "0");
        Category food = category(ctx, "Food", CategoryType.EXPENSE, true, false, false);
        keyword(ctx, food, "an sang", 10);
        QuickEntryPreviewResponse preview = quickEntryService.parse(ctx.workspace().getId(), "an sang 35k cafe 20k", ctx.user().getId());
        QuickEntryBatchConfirmRequest req = batch("voice-batch-atomic", preview);
        req.getCandidates().get(1).setType(TransactionType.ADJUSTMENT);

        assertBusinessCode(() -> quickEntryService.confirmVoiceBatch(ctx.workspace().getId(), req, ctx.user().getId()), "INVALID_TRANSACTION_TYPE");
        TestTransaction.flagForRollback();
        TestTransaction.end();
        TestTransaction.start();
        assertThat(transactionRepository.findAll()).filteredOn(tx -> tx.getWorkspace().getId().equals(ctx.workspace().getId())).isEmpty();
        assertThat(voiceRecordRepository.findAll()).filteredOn(vr -> vr.getWorkspace().getId().equals(ctx.workspace().getId())).isEmpty();
    }

    @Test
    void voiceBatchConfirmRejectsSelectedUnsupportedIntentBeforeSavingVoiceRecord() {
        TestContext ctx = createContext("qe_voice_unsupported", WorkspaceRole.OWNER);
        wallet(ctx, "Tien mat", WalletType.CASH, true, "0");
        Category food = category(ctx, "Food", CategoryType.EXPENSE, true, false, false);
        keyword(ctx, food, "an sang", 10);
        QuickEntryPreviewResponse preview = quickEntryService.parse(ctx.workspace().getId(), "an sang 35k cafe 20k", ctx.user().getId());
        QuickEntryBatchConfirmRequest req = batch("voice-batch-unsupported-intent", preview);
        req.getCandidates().get(1).setIntentType(VoiceIntentType.DEBT_CREATE);
        req.getCandidates().get(1).setType(TransactionType.EXPENSE);

        assertBusinessCode(() -> quickEntryService.confirmVoiceBatch(ctx.workspace().getId(), req, ctx.user().getId()), "VOICE_INTENT_NOT_COMMITTABLE");
        TestTransaction.flagForRollback();
        TestTransaction.end();
        TestTransaction.start();
        assertThat(transactionRepository.findAll()).filteredOn(tx -> tx.getWorkspace().getId().equals(ctx.workspace().getId())).isEmpty();
        assertThat(voiceRecordRepository.findAll()).filteredOn(vr -> vr.getWorkspace().getId().equals(ctx.workspace().getId())).isEmpty();
    }

    @Test
    void voiceBatchConfirmKeepsHistoricalDateStatusAndRequiresWritableWorkspace() {
        TestContext owner = createContext("qe_voice_hist", WorkspaceRole.OWNER);
        TestContext viewer = createContext("qe_voice_viewer", WorkspaceRole.VIEWER);
        workspaceMemberRepository.save(WorkspaceMember.builder().workspace(owner.workspace()).user(viewer.user()).role(WorkspaceRole.VIEWER).build());
        wallet(owner, "Tien mat", WalletType.CASH, true, "0");
        Category food = category(owner, "Food", CategoryType.EXPENSE, true, false, false);
        keyword(owner, food, "an sang", 10);
        QuickEntryPreviewResponse preview = quickEntryService.parse(owner.workspace().getId(), "ngay 01/06/2026 an sang 35k", owner.user().getId());
        QuickEntryBatchConfirmRequest req = batch("voice-batch-historical", preview);

        assertBusinessCode(() -> quickEntryService.confirmVoiceBatch(owner.workspace().getId(), req, viewer.user().getId()), "FORBIDDEN");
        var saved = quickEntryService.confirmVoiceBatch(owner.workspace().getId(), req, owner.user().getId());

        Transaction tx = transactionRepository.findById(saved.getItems().get(0).getTransaction().getId()).orElseThrow();
        assertThat(tx.getTransactionDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(tx.getTransactionStatus()).isEqualTo(TransactionStatus.POSTED);
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
        req.setIdempotencyKey("test-" + UUID.randomUUID());
        return req;
    }

    private QuickEntryBatchConfirmRequest batch(String idempotencyKey, QuickEntryPreviewResponse preview) {
        QuickEntryBatchConfirmRequest req = new QuickEntryBatchConfirmRequest();
        req.setIdempotencyKey(idempotencyKey);
        req.setRawInput(preview.getRawInput());
        if (preview.getCandidates().isEmpty()) {
            req.getCandidates().add(candidate("main", preview.getType(), preview.getStatus(), preview.getAmount(), preview.getWalletId(), preview.getCategoryId(), preview.getTransactionDate(), preview.getDescription()));
        } else {
            for (QuickEntryPreviewResponse.Candidate parsed : preview.getCandidates()) {
                req.getCandidates().add(candidate(parsed.getCandidateId(), parsed.getType(), parsed.getStatus(), parsed.getAmount(), parsed.getWalletId(), parsed.getCategoryId(), parsed.getTransactionDate(), parsed.getDescription()));
            }
        }
        return req;
    }

    private QuickEntryBatchConfirmRequest.CandidateConfirmRequest candidate(
            String candidateId,
            TransactionType type,
            TransactionStatus status,
            BigDecimal amount,
            UUID walletId,
            UUID categoryId,
            LocalDate transactionDate,
            String description) {
        QuickEntryBatchConfirmRequest.CandidateConfirmRequest req = new QuickEntryBatchConfirmRequest.CandidateConfirmRequest();
        req.setCandidateId(candidateId);
        req.setSelected(true);
        req.setType(type);
        req.setStatus(status);
        req.setAmount(amount);
        req.setWalletId(walletId);
        req.setCategoryId(categoryId);
        req.setTransactionDate(transactionDate);
        req.setDescription(description);
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

    private RegisterRequest registerRequest(String username) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setEmail(username + "@example.com");
        request.setPassword("Password123!");
        request.setFullName("Quick Entry HTTP User");
        return request;
    }

    private LoginRequest loginRequest(String username) {
        LoginRequest request = new LoginRequest();
        request.setIdentifier(username);
        request.setPassword("Password123!");
        return request;
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
