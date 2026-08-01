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
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.voice.dto.VoiceReviewConfirmRequest;
import com.moneyflowbackend.voice.dto.VoiceReviewConfirmResponse;
import com.moneyflowbackend.voice.dto.VoiceReviewDraftRequest;
import com.moneyflowbackend.voice.dto.VoiceReviewDraftResponse;
import com.moneyflowbackend.voice.dto.VoiceReviewDraftType;
import com.moneyflowbackend.voice.dto.VoiceReviewParseRequest;
import com.moneyflowbackend.voice.model.VoiceRecord;
import com.moneyflowbackend.voice.model.VoiceRecordStatus;
import com.moneyflowbackend.voice.repository.VoiceRecordRepository;
import com.moneyflowbackend.voice.service.VoiceReviewService;
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
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VoiceReviewIntegrationTests {
    @Autowired VoiceReviewService voiceReviewService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired CategoryKeywordRepository categoryKeywordRepository;
    @Autowired IncomeSourceRepository incomeSourceRepository;
    @Autowired VoiceRecordRepository voiceRecordRepository;
    @Autowired TransactionRepository transactionRepository;

    @Test
    void parseExpenseReturnsEditableDraftWithoutCommitting() {
        TestContext ctx = context("voice_review_parse", WorkspaceRole.OWNER);
        Wallet cash = wallet(ctx, "tien mat");
        Category gas = category(ctx, "Xang xe", CategoryType.EXPENSE);
        keyword(ctx, gas, "xang");
        long before = transactionRepository.count();

        VoiceReviewDraftResponse response = voiceReviewService.parse(ctx.workspace().getId(), parse("hom nay do xang 50 tien mat"), ctx.user().getId());

        assertThat(response.getVoiceRecordId()).isNotNull();
        assertThat(response.getStatus()).isEqualTo("NEEDS_REVIEW");
        assertThat(response.getCandidate().getType()).isEqualTo(VoiceReviewDraftType.EXPENSE);
        assertThat(response.getCandidate().getAmount()).isEqualByComparingTo("50000");
        assertThat(response.getCandidate().getWalletId()).isEqualTo(cash.getId());
        assertThat(response.getCandidate().getCategoryId()).isEqualTo(gas.getId());
        assertThat(response.getCandidate().isAffectsWalletBalance()).isTrue();
        assertThat(response.getMode()).isEqualTo("SINGLE");
        assertThat(response.getDrafts()).hasSize(1);
        assertThat(transactionRepository.count()).isEqualTo(before);
    }

    @Test
    void parseIncomeReturnsSingleDraftWithoutInventingWallet() {
        TestContext ctx = context("voice_review_income_single", WorkspaceRole.OWNER);
        wallet(ctx, "Cash");

        VoiceReviewDraftResponse response = voiceReviewService.parse(ctx.workspace().getId(), parse("hôm nay tôi kiếm được 800"), ctx.user().getId());

        assertThat(response.getMode()).isEqualTo("SINGLE");
        assertThat(response.getDrafts()).hasSize(1);
        assertThat(response.getCandidate().getType()).isEqualTo(VoiceReviewDraftType.INCOME_FACT);
        assertThat(response.getCandidate().getAmount()).isEqualByComparingTo("800000");
        assertThat(response.getCandidate().getWalletId()).isNull();
        assertThat(response.getCandidate().isAffectsWalletBalance()).isFalse();
        assertThat(response.getDrafts().get(0).isCanConfirm()).isTrue();
    }

    @Test
    void multiTranscriptReturnsSeparateDraftsWithSafeSavings() {
        TestContext ctx = context("voice_review_multi", WorkspaceRole.OWNER);
        Category gas = category(ctx, "Xăng xe", CategoryType.EXPENSE);
        keyword(ctx, gas, "xăng");

        VoiceReviewDraftResponse response = voiceReviewService.parse(ctx.workspace().getId(), parse("Hôm nay tôi kiếm được 800 cho đổ xăng hết 60.000 cửa hàng hết 50.000 tôi gửi tiết kiệm 85"), ctx.user().getId());

        assertThat(response.getMode()).isEqualTo("MULTI");
        assertThat(response.getWarnings()).contains("MULTIPLE_AMOUNTS_DETECTED");
        assertThat(response.getDrafts()).hasSize(4);
        assertThat(response.getDrafts()).extracting(draft -> draft.getCandidate().getType())
                .containsExactly(VoiceReviewDraftType.INCOME_FACT, VoiceReviewDraftType.EXPENSE, VoiceReviewDraftType.EXPENSE, VoiceReviewDraftType.SAVINGS_ALLOCATION);
        assertThat(response.getDrafts()).extracting(draft -> draft.getCandidate().getAmount())
                .containsExactly(new BigDecimal("800000"), new BigDecimal("60000"), new BigDecimal("50000"), new BigDecimal("85000"));
        assertThat(response.getDrafts().get(0).getCandidate().getWalletId()).isNull();
        assertThat(response.getDrafts().get(1).getCandidate().getCategoryId()).isEqualTo(gas.getId());
        assertThat(response.getDrafts().get(3).isCanConfirm()).isFalse();
        assertThat(response.getDrafts().get(3).getWarnings()).extracting("code")
                .contains("SAVINGS_NOT_EXPENSE", "SAVINGS_SOURCE_WALLET_REQUIRED", "SAVINGS_TARGET_NOT_SELECTED", "SAVINGS_CONFIRM_NOT_SUPPORTED");
        assertThat(response.getDrafts().get(3).getCandidate().isCountsAsExpense()).isFalse();
        assertThat(response.getDrafts().get(3).getCandidate().isCategoryRequired()).isFalse();
        assertThat(response.getDrafts().get(3).getCandidate().getNeedsFields()).contains("sourceWalletId", "targetFundId");
    }

    @Test
    void exactFinancialInboxTranscriptParseReturnsSafeMultiDraft() {
        TestContext ctx = context("voice_review_truth", WorkspaceRole.OWNER);
        Category fuel = category(ctx, "Xăng xe", CategoryType.EXPENSE);
        keyword(ctx, fuel, "xăng");
        long before = transactionRepository.count();

        VoiceReviewDraftResponse response = voiceReviewService.parse(ctx.workspace().getId(),
                parse("Hôm nay đã kiếm được 800 Tôi ăn hết 50 Cái đổ xăng hết 65.000 Ta gửi tiết kiệm hết 35"), ctx.user().getId());

        assertThat(response.getMode()).isEqualTo("MULTI");
        assertThat(response.getStatus()).isEqualTo("NEEDS_REVIEW");
        assertThat(response.getVoiceRecordId()).isNotNull();
        assertThat(response.getWarnings()).contains("VOICE_MULTI_INTENT_DETECTED");
        assertThat(response.getDrafts()).hasSize(4);
        assertThat(response.getDrafts()).extracting(draft -> draft.getCandidate().getType())
                .containsExactly(VoiceReviewDraftType.INCOME_FACT, VoiceReviewDraftType.EXPENSE, VoiceReviewDraftType.EXPENSE, VoiceReviewDraftType.SAVINGS_ALLOCATION);
        assertThat(response.getDrafts()).extracting(draft -> draft.getCandidate().getAmount())
                .containsExactly(new BigDecimal("800000"), new BigDecimal("50000"), new BigDecimal("65000"), new BigDecimal("35000"));
        assertThat(response.getDrafts().get(0).getCandidate().isWalletRequired()).isFalse();
        assertThat(response.getDrafts().get(0).getCandidate().isAffectsWalletBalance()).isFalse();
        assertThat(response.getDrafts().get(2).getCandidate().getCategoryId()).isEqualTo(fuel.getId());
        assertThat(response.getDrafts().get(3).getCandidate().isCountsAsExpense()).isFalse();
        assertThat(transactionRepository.count()).isEqualTo(before);
    }

    @Test
    void patchDraftUpdatesFieldsWithoutCreatingTransaction() {
        Fixture fixture = fixture("voice_review_patch");
        VoiceRecord record = record(fixture.ctx(), VoiceRecordStatus.PARSED);
        long before = transactionRepository.count();

        VoiceReviewDraftResponse response = voiceReviewService.patchDraft(
                fixture.ctx().workspace().getId(),
                record.getId(),
                draft(VoiceReviewDraftType.EXPENSE, "75000", fixture.cash(), fixture.gas(), null, "updated gas"),
                fixture.ctx().user().getId());

        assertThat(response.getCandidate().getAmount()).isEqualByComparingTo("75000");
        assertThat(response.getCandidate().getCategoryName()).isEqualTo("Gas");
        assertThat(response.getCandidate().getWalletName()).isEqualTo("Cash");
        assertThat(response.getCandidate().getNote()).isEqualTo("updated gas");
        assertThat(transactionRepository.count()).isEqualTo(before);
    }

    @Test
    void confirmValidExpenseCreatesExactlyOnePostedTransaction() {
        Fixture fixture = fixture("voice_review_confirm");
        VoiceRecord record = record(fixture.ctx(), VoiceRecordStatus.PARSED);

        VoiceReviewConfirmResponse response = confirmExpense(fixture, record);

        assertThat(response.getStatus()).isEqualTo("POSTED");
        assertThat(response.getTransactionId()).isNotNull();
        assertThat(transactionRepository.findAllByWorkspaceIdAndVoiceRecordIdAndSourceTypeOrderByCreatedAtAsc(
                fixture.ctx().workspace().getId(), record.getId(), com.moneyflowbackend.transaction.model.TransactionSourceType.VOICE)).hasSize(1);
    }

    @Test
    void confirmingSameVoiceTwiceDoesNotDuplicateTransaction() {
        Fixture fixture = fixture("voice_review_idempotent");
        VoiceRecord record = record(fixture.ctx(), VoiceRecordStatus.PARSED);

        VoiceReviewConfirmResponse first = confirmExpense(fixture, record);
        VoiceReviewConfirmResponse second = confirmExpense(fixture, record);

        assertThat(second.getTransactionId()).isEqualTo(first.getTransactionId());
        assertThat(transactionRepository.findAllByWorkspaceIdAndVoiceRecordIdAndSourceTypeOrderByCreatedAtAsc(
                fixture.ctx().workspace().getId(), record.getId(), com.moneyflowbackend.transaction.model.TransactionSourceType.VOICE)).hasSize(1);
    }

    @Test
    void confirmOneDraftDoesNotConfirmAllAndIsIdempotent() {
        Fixture fixture = fixture("voice_review_confirm_one");
        VoiceReviewDraftResponse parsed = voiceReviewService.parse(fixture.ctx().workspace().getId(), parse("đổ xăng hết 60.000 ăn hết 50.000"), fixture.ctx().user().getId());
        VoiceReviewDraftResponse.DraftItem firstDraft = parsed.getDrafts().get(0);
        VoiceReviewConfirmRequest req = new VoiceReviewConfirmRequest();
        req.setCandidate(draft(VoiceReviewDraftType.EXPENSE, "60000", fixture.cash(), fixture.gas(), null, "gas"));

        VoiceReviewConfirmResponse first = voiceReviewService.confirmDraft(fixture.ctx().workspace().getId(), parsed.getVoiceRecordId(), firstDraft.getDraftId(), req, fixture.ctx().user().getId());
        VoiceReviewConfirmResponse replay = voiceReviewService.confirmDraft(fixture.ctx().workspace().getId(), parsed.getVoiceRecordId(), firstDraft.getDraftId(), req, fixture.ctx().user().getId());

        assertThat(replay.getTransactionId()).isEqualTo(first.getTransactionId());
        assertThat(transactionRepository.findAllByWorkspaceIdAndVoiceRecordIdAndSourceTypeOrderByCreatedAtAsc(
                fixture.ctx().workspace().getId(), parsed.getVoiceRecordId(), com.moneyflowbackend.transaction.model.TransactionSourceType.VOICE)).hasSize(1);
    }

    @Test
    void patchDraftIdUpdatesOnlyRequestedDraft() {
        Fixture fixture = fixture("voice_review_patch_one");
        VoiceReviewDraftResponse parsed = voiceReviewService.parse(fixture.ctx().workspace().getId(), parse("đổ xăng hết 60.000 ăn hết 50.000"), fixture.ctx().user().getId());
        String secondDraftId = parsed.getDrafts().get(1).getDraftId();

        VoiceReviewDraftResponse patched = voiceReviewService.patchDraft(
                fixture.ctx().workspace().getId(),
                parsed.getVoiceRecordId(),
                secondDraftId,
                draft(VoiceReviewDraftType.EXPENSE, "50000", fixture.cash(), fixture.gas(), null, "food"),
                fixture.ctx().user().getId());

        assertThat(patched.getDrafts()).hasSize(1);
        assertThat(patched.getDrafts().get(0).getDraftId()).isEqualTo(secondDraftId);
        assertThat(patched.getCandidate().getNote()).isEqualTo("food");
    }

    @Test
    void invalidDraftIdReturnsFriendlyNotFound() {
        Fixture fixture = fixture("voice_review_bad_draft");
        VoiceReviewDraftResponse parsed = voiceReviewService.parse(fixture.ctx().workspace().getId(), parse("đổ xăng hết 60.000 ăn hết 50.000"), fixture.ctx().user().getId());

        assertBusinessCode(() -> voiceReviewService.patchDraft(fixture.ctx().workspace().getId(), parsed.getVoiceRecordId(), "missing", draft(VoiceReviewDraftType.EXPENSE, "50000", fixture.cash(), fixture.gas(), null, "bad"), fixture.ctx().user().getId()),
                "VOICE_DRAFT_NOT_FOUND");
    }

    @Test
    void missingWalletOrCategoryReturnsIncompleteDraftCode() {
        Fixture fixture = fixture("voice_review_missing");
        VoiceRecord record = record(fixture.ctx(), VoiceRecordStatus.PARSED);

        VoiceReviewConfirmRequest req = new VoiceReviewConfirmRequest();
        req.setCandidate(draft(VoiceReviewDraftType.EXPENSE, "50000", null, null, null, "missing"));

        assertBusinessCode(() -> voiceReviewService.confirm(fixture.ctx().workspace().getId(), record.getId(), req, fixture.ctx().user().getId()),
                "VOICE_DRAFT_INCOMPLETE");
    }

    @Test
    void incomeWithoutExplicitWalletDoesNotInventWallet() {
        TestContext ctx = context("voice_review_income", WorkspaceRole.OWNER);
        wallet(ctx, "Cash");
        incomeSource(ctx, "Income source");

        VoiceReviewDraftResponse response = voiceReviewService.parse(ctx.workspace().getId(), parse("hom nay toi kiem duoc 800"), ctx.user().getId());

        assertThat(response.getCandidate().getType()).isEqualTo(VoiceReviewDraftType.INCOME_FACT);
        assertThat(response.getCandidate().getWalletId()).isNull();
        assertThat(response.getCandidate().isAffectsWalletBalance()).isFalse();
        assertThat(response.getCandidate().getNeedsFields()).doesNotContain("walletId");
    }

    @Test
    void confirmIncomeFactWithoutWalletDoesNotAffectWalletAndIsIdempotent() {
        Fixture fixture = fixture("vr_income_fact");
        VoiceRecord record = record(fixture.ctx(), VoiceRecordStatus.PARSED);

        VoiceReviewConfirmRequest req = new VoiceReviewConfirmRequest();
        req.setCandidate(draft(VoiceReviewDraftType.INCOME_FACT, "800000", null, null, null, "income fact"));

        VoiceReviewConfirmResponse first = voiceReviewService.confirm(fixture.ctx().workspace().getId(), record.getId(), req, fixture.ctx().user().getId());
        VoiceReviewConfirmResponse second = voiceReviewService.confirm(fixture.ctx().workspace().getId(), record.getId(), req, fixture.ctx().user().getId());

        assertThat(second.getTransactionId()).isEqualTo(first.getTransactionId());
        assertThat(first.getTransaction().getWalletId()).isNull();
        assertThat(first.getTransaction().isAffectsWalletBalance()).isFalse();
        assertThat(transactionRepository.findAllByWorkspaceIdAndVoiceRecordIdAndSourceTypeOrderByCreatedAtAsc(
                fixture.ctx().workspace().getId(), record.getId(), com.moneyflowbackend.transaction.model.TransactionSourceType.VOICE)).hasSize(1);
    }

    @Test
    void parseWalletSnapshotDraftMatchesWalletAndDoesNotBecomeIncome() {
        TestContext ctx = context("voice_review_snapshot", WorkspaceRole.OWNER);
        Wallet mb = wallet(ctx, "MB Bank");

        VoiceReviewDraftResponse response = voiceReviewService.parse(ctx.workspace().getId(), parse("MB con 4 trieu 8"), ctx.user().getId());

        assertThat(response.getCandidate().getType()).isEqualTo(VoiceReviewDraftType.WALLET_SNAPSHOT);
        assertThat(response.getCandidate().getAmount()).isEqualByComparingTo("4800000");
        assertThat(response.getCandidate().getWalletId()).isEqualTo(mb.getId());
        assertThat(response.getCandidate().isAffectsWalletBalance()).isFalse();
        assertThat(response.getDrafts().get(0).isCanConfirm()).isFalse();
        assertThat(response.getDrafts().get(0).getWarnings()).extracting("code").contains("WALLET_SNAPSHOT_CONFIRM_NOT_SUPPORTED");
    }

    @Test
    void walletSnapshotConfirmReturnsCleanUnsupportedAndCreatesNoTransaction() {
        TestContext ctx = context("voice_review_snapshot_confirm", WorkspaceRole.OWNER);
        Wallet mb = wallet(ctx, "MB Bank");
        VoiceRecord record = record(ctx, VoiceRecordStatus.PARSED);
        long before = transactionRepository.count();

        VoiceReviewConfirmRequest req = new VoiceReviewConfirmRequest();
        req.setCandidate(draft(VoiceReviewDraftType.WALLET_SNAPSHOT, "4800000", mb, null, null, "MB con 4 trieu 8"));

        assertBusinessCode(() -> voiceReviewService.confirm(ctx.workspace().getId(), record.getId(), req, ctx.user().getId()),
                "WALLET_SNAPSHOT_CONFIRM_NOT_SUPPORTED");
        assertThat(transactionRepository.count()).isEqualTo(before);
    }

    @Test
    void mixedIncomeFactSnapshotAndExpenseReturnSeparateDraftTypes() {
        TestContext ctx = context("voice_review_mixed_snapshot", WorkspaceRole.OWNER);
        Wallet mb = wallet(ctx, "MB Bank");
        Category food = category(ctx, "An uong", CategoryType.EXPENSE);
        keyword(ctx, food, "an");

        VoiceReviewDraftResponse response = voiceReviewService.parse(ctx.workspace().getId(),
                parse("Hom nay kiem duoc 800, MB con 4tr8, toi an 50k"), ctx.user().getId());

        assertThat(response.getMode()).isEqualTo("MULTI");
        assertThat(response.getDrafts()).extracting(draft -> draft.getCandidate().getType())
                .containsExactly(VoiceReviewDraftType.INCOME_FACT, VoiceReviewDraftType.WALLET_SNAPSHOT, VoiceReviewDraftType.EXPENSE);
        assertThat(response.getDrafts().get(1).getCandidate().getWalletId()).isEqualTo(mb.getId());
        assertThat(response.getDrafts().get(2).getCandidate().getCategoryId()).isEqualTo(food.getId());
    }

    @Test
    void debtPhraseIsNotConvertedToNormalExpense() {
        Fixture fixture = fixture("voice_review_debt");

        VoiceReviewDraftResponse response = voiceReviewService.parse(fixture.ctx().workspace().getId(), parse("Bao tra no toi 500"), fixture.ctx().user().getId());

        assertThat(response.getCandidate().getType()).isEqualTo(VoiceReviewDraftType.LOAN_COLLECTION);
        assertThat(response.getCandidate().getDebtDirection()).isEqualTo("RECEIVABLE");
        assertThat(response.getCandidate().isCountsAsIncome()).isFalse();
        assertThat(response.getCandidate().isCountsAsExpense()).isFalse();
        assertThat(response.getCandidate().isCategoryRequired()).isFalse();
        assertThat(response.getCandidate().getNeedsFields()).contains("debtId", "destinationWalletId");
        assertThat(response.getDrafts().get(0).getWarnings()).extracting("code")
                .contains("DEBT_NOT_EXPENSE", "DEBT_NOT_INCOME", "DEBT_WALLET_REQUIRED", "DEBT_NOT_FOUND", "DEBT_CONFIRM_NOT_SUPPORTED");
    }

    @Test
    void debtMovementDraftsKeepDomainTypesAndNeverBecomeIncomeOrExpense() {
        Fixture fixture = fixture("voice_review_debt_types");

        VoiceReviewDraftResponse disbursement = voiceReviewService.parse(fixture.ctx().workspace().getId(), parse("cho Nam muon 500k"), fixture.ctx().user().getId());
        VoiceReviewDraftResponse collection = voiceReviewService.parse(fixture.ctx().workspace().getId(), parse("Nam tra toi 200k"), fixture.ctx().user().getId());
        VoiceReviewDraftResponse borrowing = voiceReviewService.parse(fixture.ctx().workspace().getId(), parse("toi muon Nam 1 trieu"), fixture.ctx().user().getId());
        VoiceReviewDraftResponse repayment = voiceReviewService.parse(fixture.ctx().workspace().getId(), parse("toi tra no Nam 100k"), fixture.ctx().user().getId());

        assertDebtDraft(disbursement, VoiceReviewDraftType.LOAN_DISBURSEMENT, "RECEIVABLE", "sourceWalletId", "counterpartyId");
        assertDebtDraft(collection, VoiceReviewDraftType.LOAN_COLLECTION, "RECEIVABLE", "destinationWalletId", "debtId");
        assertDebtDraft(borrowing, VoiceReviewDraftType.BORROWING_RECEIPT, "PAYABLE", "destinationWalletId", "counterpartyId");
        assertDebtDraft(repayment, VoiceReviewDraftType.BORROWING_REPAYMENT, "PAYABLE", "sourceWalletId", "debtId");
    }

    @Test
    void debtDraftConfirmIsUnsupportedAndCreatesNoTransaction() {
        Fixture fixture = fixture("voice_review_debt_confirm");
        VoiceReviewDraftResponse parsed = voiceReviewService.parse(fixture.ctx().workspace().getId(), parse("cho Nam muon 500k"), fixture.ctx().user().getId());
        long before = transactionRepository.count();

        assertBusinessCode(() -> voiceReviewService.confirmDraft(fixture.ctx().workspace().getId(), parsed.getVoiceRecordId(), parsed.getDrafts().get(0).getDraftId(), null, fixture.ctx().user().getId()),
                "DEBT_CONFIRM_NOT_SUPPORTED");
        assertThat(transactionRepository.count()).isEqualTo(before);
    }

    @Test
    void mixedExpenseAndDebtMovementsReturnSeparateDraftTypes() {
        Fixture fixture = fixture("voice_review_debt_multi");

        VoiceReviewDraftResponse response = voiceReviewService.parse(fixture.ctx().workspace().getId(),
                parse("hom nay an 50k, cho Nam muon 500k, Nam tra toi 200k"), fixture.ctx().user().getId());

        assertThat(response.getMode()).isEqualTo("MULTI");
        assertThat(response.getDrafts()).extracting(draft -> draft.getCandidate().getType())
                .containsExactly(VoiceReviewDraftType.EXPENSE, VoiceReviewDraftType.LOAN_DISBURSEMENT, VoiceReviewDraftType.LOAN_COLLECTION);
        assertThat(response.getDrafts().get(1).getCandidate().isCountsAsExpense()).isFalse();
        assertThat(response.getDrafts().get(2).getCandidate().isCountsAsIncome()).isFalse();
    }

    @Test
    void savingsDraftCannotConfirmAsNormalTransaction() {
        Fixture fixture = fixture("voice_review_savings");
        VoiceReviewDraftResponse parsed = voiceReviewService.parse(fixture.ctx().workspace().getId(), parse("tôi gửi tiết kiệm 85"), fixture.ctx().user().getId());

        long before = transactionRepository.count();

        assertThat(parsed.getCandidate().getType()).isEqualTo(VoiceReviewDraftType.SAVINGS_ALLOCATION);
        assertThat(parsed.getCandidate().isAffectsWalletBalance()).isFalse();
        assertThat(parsed.getCandidate().isCountsAsExpense()).isFalse();
        assertThat(parsed.getCandidate().isCategoryRequired()).isFalse();
        assertThat(parsed.getCandidate().getNeedsFields()).contains("sourceWalletId", "targetFundId");
        assertThat(parsed.getDrafts().get(0).getWarnings()).extracting("code")
                .contains("SAVINGS_NOT_EXPENSE", "SAVINGS_SOURCE_WALLET_REQUIRED", "SAVINGS_TARGET_NOT_SELECTED", "SAVINGS_CONFIRM_NOT_SUPPORTED");
        assertBusinessCode(() -> voiceReviewService.confirmDraft(fixture.ctx().workspace().getId(), parsed.getVoiceRecordId(), parsed.getDrafts().get(0).getDraftId(), null, fixture.ctx().user().getId()),
                "SAVINGS_CONFIRM_NOT_SUPPORTED");
        assertThat(transactionRepository.count()).isEqualTo(before);
    }

    @Test
    void fundDraftsKeepSpecificTypesAndNeverNeedCategory() {
        Fixture fixture = fixture("voice_review_funds");

        VoiceReviewDraftResponse sinking = voiceReviewService.parse(fixture.ctx().workspace().getId(), parse("bo vao quy laptop 200k"), fixture.ctx().user().getId());
        VoiceReviewDraftResponse emergency = voiceReviewService.parse(fixture.ctx().workspace().getId(), parse("quy khan cap 100k"), fixture.ctx().user().getId());

        assertThat(sinking.getCandidate().getType()).isEqualTo(VoiceReviewDraftType.SINKING_FUND_CONTRIBUTION);
        assertThat(sinking.getCandidate().isAffectsWalletBalance()).isFalse();
        assertThat(sinking.getCandidate().isCategoryRequired()).isFalse();
        assertThat(sinking.getCandidate().getNeedsFields()).contains("sourceWalletId", "targetFundId");
        assertThat(emergency.getCandidate().getType()).isEqualTo(VoiceReviewDraftType.EMERGENCY_FUND_CONTRIBUTION);
        assertThat(emergency.getCandidate().isCountsAsExpense()).isFalse();
        assertThat(emergency.getCandidate().getNeedsFields()).contains("sourceWalletId", "targetFundId");
    }

    @Test
    void audioStatusIsPreservedInReviewAndConfirmResponses() {
        Fixture fixture = fixture("voice_review_audio");
        VoiceRecord record = record(fixture.ctx(), VoiceRecordStatus.AUDIO_STORED);
        record.setAudioStorageKey("voice/audio.webm");
        voiceRecordRepository.saveAndFlush(record);

        VoiceReviewDraftResponse draft = voiceReviewService.patchDraft(
                fixture.ctx().workspace().getId(),
                record.getId(),
                draft(VoiceReviewDraftType.EXPENSE, "50000", fixture.cash(), fixture.gas(), null, "gas"),
                fixture.ctx().user().getId());
        VoiceReviewConfirmResponse confirm = confirmExpense(fixture, record);

        assertThat(draft.getAudioStatus()).isEqualTo("AUDIO_STORED");
        assertThat(confirm.getAudioStatus()).isEqualTo("AUDIO_STORED");
        assertThat(confirm.getTransaction().getAudioStatus()).isEqualTo("AUDIO_STORED");
    }

    @Test
    void unauthorizedWorkspaceAccessIsRejected() {
        Fixture fixture = fixture("voice_review_auth");
        TestContext other = context("voice_review_auth_other", WorkspaceRole.OWNER);
        VoiceRecord record = record(fixture.ctx(), VoiceRecordStatus.PARSED);

        assertBusinessCode(() -> voiceReviewService.patchDraft(fixture.ctx().workspace().getId(), record.getId(),
                draft(VoiceReviewDraftType.EXPENSE, "50000", fixture.cash(), fixture.gas(), null, "gas"), other.user().getId()),
                "WORKSPACE_ACCESS_DENIED");
    }

    @Test
    void crossWorkspaceWalletCategoryAndSourceAreRejected() {
        Fixture fixture = fixture("voice_review_refs");
        Fixture other = fixture("voice_review_refs_other");
        VoiceRecord record = record(fixture.ctx(), VoiceRecordStatus.PARSED);

        assertBusinessCode(() -> voiceReviewService.patchDraft(fixture.ctx().workspace().getId(), record.getId(),
                draft(VoiceReviewDraftType.EXPENSE, "50000", other.cash(), fixture.gas(), null, "bad wallet"), fixture.ctx().user().getId()),
                "WALLET_NOT_FOUND");
        assertBusinessCode(() -> voiceReviewService.patchDraft(fixture.ctx().workspace().getId(), record.getId(),
                draft(VoiceReviewDraftType.EXPENSE, "50000", fixture.cash(), other.gas(), null, "bad category"), fixture.ctx().user().getId()),
                "CATEGORY_NOT_FOUND");
        assertBusinessCode(() -> voiceReviewService.patchDraft(fixture.ctx().workspace().getId(), record.getId(),
                draft(VoiceReviewDraftType.INCOME, "50000", fixture.cash(), null, other.incomeSource(), "bad source"), fixture.ctx().user().getId()),
                "INCOME_SOURCE_NOT_FOUND");
    }

    private VoiceReviewConfirmResponse confirmExpense(Fixture fixture, VoiceRecord record) {
        VoiceReviewConfirmRequest req = new VoiceReviewConfirmRequest();
        req.setCandidate(draft(VoiceReviewDraftType.EXPENSE, "50000", fixture.cash(), fixture.gas(), null, "gas"));
        return voiceReviewService.confirm(fixture.ctx().workspace().getId(), record.getId(), req, fixture.ctx().user().getId());
    }

    private Fixture fixture(String prefix) {
        TestContext ctx = context(prefix, WorkspaceRole.OWNER);
        Wallet cash = wallet(ctx, "Cash");
        Category gas = category(ctx, "Gas", CategoryType.EXPENSE);
        IncomeSource source = incomeSource(ctx, "Income source");
        keyword(ctx, gas, "gas");
        return new Fixture(ctx, cash, gas, source);
    }

    private VoiceReviewParseRequest parse(String text) {
        VoiceReviewParseRequest req = new VoiceReviewParseRequest();
        req.setTranscript(text);
        return req;
    }

    private VoiceReviewDraftRequest draft(VoiceReviewDraftType type, String amount, Wallet wallet, Category category, IncomeSource source, String note) {
        VoiceReviewDraftRequest req = new VoiceReviewDraftRequest();
        req.setType(type);
        req.setAmount(new BigDecimal(amount));
        req.setOccurredAt(OffsetDateTime.parse("2026-07-30T19:00:00+07:00"));
        req.setWalletId(wallet == null ? null : wallet.getId());
        req.setCategoryId(category == null ? null : category.getId());
        req.setIncomeSourceId(source == null ? null : source.getId());
        req.setNote(note);
        return req;
    }

    private VoiceRecord record(TestContext ctx, VoiceRecordStatus status) {
        return voiceRecordRepository.saveAndFlush(VoiceRecord.builder()
                .workspace(ctx.workspace())
                .createdByUser(ctx.user())
                .originalTranscript("voice draft")
                .editedTranscript("voice draft")
                .voiceStatus(status)
                .build());
    }

    private TestContext context(String prefix, WorkspaceRole role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .fullName("Voice Review Test User")
                .build());
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .name(prefix + " workspace")
                .createdByUser(user)
                .timezone("Asia/Ho_Chi_Minh")
                .quickAmountUnit("THOUSAND")
                .currency("VND")
                .build());
        workspaceMemberRepository.save(WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(role)
                .build());
        return new TestContext(user, workspace);
    }

    private Wallet wallet(TestContext ctx, String name) {
        return walletRepository.saveAndFlush(Wallet.builder()
                .workspace(ctx.workspace())
                .name(name)
                .walletType(WalletType.CASH)
                .openingBalance(BigDecimal.ZERO)
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

    private IncomeSource incomeSource(TestContext ctx, String name) {
        return incomeSourceRepository.saveAndFlush(IncomeSource.builder()
                .workspace(ctx.workspace())
                .name(name)
                .type(IncomeSourceType.OTHER)
                .status(IncomeSourceStatus.ACTIVE)
                .createdByUser(ctx.user())
                .build());
    }

    private void keyword(TestContext ctx, Category category, String value) {
        categoryKeywordRepository.saveAndFlush(CategoryKeyword.builder()
                .workspace(ctx.workspace())
                .category(category)
                .keyword(value)
                .priority(10)
                .isUserLearned(true)
                .build());
    }

    private void assertBusinessCode(ThrowingRunnable runnable, String code) {
        assertThatThrownBy(runnable::run)
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(code);
    }

    private void assertDebtDraft(VoiceReviewDraftResponse response, VoiceReviewDraftType type, String direction, String walletField, String referenceField) {
        assertThat(response.getCandidate().getType()).isEqualTo(type);
        assertThat(response.getCandidate().getDebtDirection()).isEqualTo(direction);
        assertThat(response.getCandidate().isAffectsWalletBalance()).isTrue();
        assertThat(response.getCandidate().isCountsAsIncome()).isFalse();
        assertThat(response.getCandidate().isCountsAsExpense()).isFalse();
        assertThat(response.getCandidate().isCategoryRequired()).isFalse();
        assertThat(response.getCandidate().isCanConfirm()).isFalse();
        assertThat(response.getCandidate().getNeedsFields()).contains(walletField, referenceField);
        assertThat(response.getDrafts().get(0).getWarnings()).extracting("code").contains("DEBT_CONFIRM_NOT_SUPPORTED");
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }

    private record TestContext(User user, Workspace workspace) {}
    private record Fixture(TestContext ctx, Wallet cash, Category gas, IncomeSource incomeSource) {}
}
