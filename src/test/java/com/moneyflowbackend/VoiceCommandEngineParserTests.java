package com.moneyflowbackend;

import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryKeyword;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.quickentry.dto.QuickEntryPreviewResponse;
import com.moneyflowbackend.quickentry.dto.VoiceCandidateStatus;
import com.moneyflowbackend.quickentry.dto.VoiceIntentType;
import com.moneyflowbackend.quickentry.dto.VoiceLedgerEffect;
import com.moneyflowbackend.quickentry.parser.QuickAmountParser;
import com.moneyflowbackend.quickentry.parser.QuickDateParser;
import com.moneyflowbackend.quickentry.parser.QuickEntryParser;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.model.WalletType;
import com.moneyflowbackend.workspace.model.Workspace;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VoiceCommandEngineParserTests {
    private final QuickEntryParser parser = new QuickEntryParser(
            new QuickAmountParser(),
            new QuickDateParser(Clock.fixed(Instant.parse("2026-07-23T01:00:00Z"), ZoneOffset.UTC)));

    @Test
    void parsesMixedIncomeExpenseTranscriptIntoSeparateCandidates() {
        Fixture f = fixture();

        QuickEntryPreviewResponse preview = parser.parse(
                "Hôm nay tôi kiếm được 800.000 tôi ăn hết năm chục tôi đổ xăng hết 80 tôi uống nước hết 25.000",
                f.workspace(), f.keywords(), f.categories(), f.wallets());

        assertThat(preview.getWarnings()).contains("MULTIPLE_ITEMS_DETECTED");
        assertThat(preview.getCandidates()).hasSize(4);
        assertThat(preview.getCandidates()).extracting(QuickEntryPreviewResponse.Candidate::getIntentType)
                .containsExactly(
                        VoiceIntentType.TRANSACTION_INCOME,
                        VoiceIntentType.TRANSACTION_EXPENSE,
                        VoiceIntentType.TRANSACTION_EXPENSE,
                        VoiceIntentType.TRANSACTION_EXPENSE);
        assertThat(preview.getCandidates()).extracting(QuickEntryPreviewResponse.Candidate::getAmount)
                .containsExactly(
                        new BigDecimal("800000"),
                        new BigDecimal("50000"),
                        new BigDecimal("80000"),
                        new BigDecimal("25000"));
        assertThat(preview.getCandidates().get(1).getDescription()).contains("ăn");
        assertThat(preview.getCandidates().get(2).getDescription()).contains("đổ xăng");
        assertThat(preview.getCandidates().get(3).getDescription()).contains("uống nước");
        assertThat(preview.getCandidates().get(2).getCategoryName()).isEqualTo("Xăng xe");
    }

    @Test
    void nonTransactionCommandsBecomeVisibleTypedDrafts() {
        Fixture f = fixture();

        assertDraft(parser.parse("Bảo nợ tôi 500k", f.workspace(), f.keywords(), f.categories(), f.wallets()), VoiceIntentType.DEBT_CREATE_RECEIVABLE, "500000");
        assertDraft(parser.parse("cho Bảo mượn 500k tiền mặt", f.workspace(), f.keywords(), f.categories(), f.wallets()), VoiceIntentType.LOAN_DISBURSEMENT, "500000");
        assertDraft(parser.parse("tôi trả chị Nga 1 triệu", f.workspace(), f.keywords(), f.categories(), f.wallets()), VoiceIntentType.DEBT_PAYMENT, "1000000");
        assertDraft(parser.parse("bỏ 500k vào quỹ khẩn cấp từ ví tiền mặt", f.workspace(), f.keywords(), f.categories(), f.wallets()), VoiceIntentType.EMERGENCY_FUND_CONTRIBUTION, "500000");
        assertDraft(parser.parse("ví tiền mặt của anh còn 2 triệu", f.workspace(), f.keywords(), f.categories(), f.wallets()), VoiceIntentType.WALLET_BALANCE_SNAPSHOT, "2000000");

        QuickEntryPreviewResponse stats = parser.parse("tháng này tôi tiêu bao nhiêu", f.workspace(), f.keywords(), f.categories(), f.wallets());
        assertThat(stats.getIntentType()).isEqualTo(VoiceIntentType.STAT_QUERY);
        assertThat(stats.getCandidateStatus()).isEqualTo(VoiceCandidateStatus.READ_ONLY);
        assertThat(stats.getLedgerEffect()).isEqualTo(VoiceLedgerEffect.READ_ONLY);
        assertThat(stats.getType()).isNull();
        assertThat(stats.getAmount()).isNull();
        assertThat(stats.isReadyToConfirm()).isFalse();
    }

    @Test
    void domainContractRoutesKnownFinancialFacts() {
        Fixture f = fixture();

        assertDraft(parser.parse("Ví tiền mặt còn 2 triệu", f.workspace(), f.keywords(), f.categories(), f.wallets()), VoiceIntentType.WALLET_BALANCE_SNAPSHOT, "2000000");
        assertDraft(parser.parse("Bảo nợ tôi 500", f.workspace(), f.keywords(), f.categories(), f.wallets()), VoiceIntentType.DEBT_CREATE_RECEIVABLE, "500000");
        assertDraft(parser.parse("Tôi nợ mẹ 1 triệu", f.workspace(), f.keywords(), f.categories(), f.wallets()), VoiceIntentType.DEBT_CREATE_PAYABLE, "1000000");
        assertDraft(parser.parse("Cho Bảo mượn 500 tiền mặt", f.workspace(), f.keywords(), f.categories(), f.wallets()), VoiceIntentType.LOAN_DISBURSEMENT, "500000");
        assertDraft(parser.parse("Bảo trả tôi 200 tiền mặt", f.workspace(), f.keywords(), f.categories(), f.wallets()), VoiceIntentType.LOAN_COLLECTION, "200000");
        assertDraft(parser.parse("Tôi trả lãi 20", f.workspace(), f.keywords(), f.categories(), f.wallets()), VoiceIntentType.INTEREST_EXPENSE, "20000");
        assertDraft(parser.parse("Nhận lãi ngân hàng 20", f.workspace(), f.keywords(), f.categories(), f.wallets()), VoiceIntentType.INTEREST_INCOME, "20000");
        assertDraft(parser.parse("Bỏ 500 vào quỹ khẩn cấp", f.workspace(), f.keywords(), f.categories(), f.wallets()), VoiceIntentType.EMERGENCY_FUND_CONTRIBUTION, "500000");
        assertDraft(parser.parse("Gửi tiết kiệm 1 triệu", f.workspace(), f.keywords(), f.categories(), f.wallets()), VoiceIntentType.SAVINGS_GOAL_CONTRIBUTION, "1000000");

        QuickEntryPreviewResponse stat = parser.parse("Tháng này tôi tiêu bao nhiêu", f.workspace(), f.keywords(), f.categories(), f.wallets());
        assertThat(stat.getIntentType()).isEqualTo(VoiceIntentType.STAT_QUERY);
        assertThat(stat.getCandidateStatus()).isEqualTo(VoiceCandidateStatus.READ_ONLY);
        assertThat(stat.getLedgerEffect()).isEqualTo(VoiceLedgerEffect.READ_ONLY);
        assertThat(stat.getType()).isNull();
    }

    @Test
    void mixedContractKeepsCandidateOrderAndNoKnownIntentBecomesUnsupported() {
        Fixture f = fixture();

        QuickEntryPreviewResponse preview = parser.parse(
                "Hôm nay tôi kiếm được 800 ăn 50 đổ xăng 60 ví tiền mặt còn 500 bỏ 100 vào quỹ khẩn cấp Bảo nợ tôi 200 tháng này tôi tiêu bao nhiêu",
                f.workspace(), f.keywords(), f.categories(), f.wallets());

        assertThat(preview.getCandidates()).hasSize(7);
        assertThat(preview.getCandidates()).extracting(QuickEntryPreviewResponse.Candidate::getIntentType)
                .containsExactly(
                        VoiceIntentType.TRANSACTION_INCOME,
                        VoiceIntentType.TRANSACTION_EXPENSE,
                        VoiceIntentType.TRANSACTION_EXPENSE,
                        VoiceIntentType.WALLET_BALANCE_SNAPSHOT,
                        VoiceIntentType.EMERGENCY_FUND_CONTRIBUTION,
                        VoiceIntentType.DEBT_CREATE_RECEIVABLE,
                        VoiceIntentType.STAT_QUERY);
        assertThat(preview.getCandidates()).extracting(QuickEntryPreviewResponse.Candidate::getAmount)
                .containsExactly(
                        new BigDecimal("800000"),
                        new BigDecimal("50000"),
                        new BigDecimal("60000"),
                        new BigDecimal("500000"),
                        new BigDecimal("100000"),
                        new BigDecimal("200000"),
                        null);
        assertThat(preview.getCandidates()).noneSatisfy(candidate ->
                assertThat(candidate.getIntentType()).isEqualTo(VoiceIntentType.UNKNOWN_UNSUPPORTED));
        assertThat(preview.getCandidates().get(0).getWalletId()).isNull();
        assertThat(preview.getCandidates().get(0).getLedgerEffect()).isEqualTo(VoiceLedgerEffect.NEEDS_WALLET_REVIEW);
        assertThat(preview.getCandidates().get(1).isCommitSupported()).isTrue();
        assertThat(preview.getCandidates().get(1).getSpendingScope()).isEqualTo(com.moneyflowbackend.common.model.SpendingScope.PERSONAL);
        assertThat(preview.getCandidates().get(6).getLedgerEffect()).isEqualTo(VoiceLedgerEffect.READ_ONLY);
    }

    private void assertDraft(QuickEntryPreviewResponse preview, VoiceIntentType intentType, String amount) {
        assertThat(preview.getIntentType()).isEqualTo(intentType);
        assertThat(preview.getCandidateStatus()).isEqualTo(VoiceCandidateStatus.MANUAL);
        assertThat(preview.getLedgerEffect()).isIn(VoiceLedgerEffect.MANUAL_UNSUPPORTED, VoiceLedgerEffect.DOES_NOT_AFFECT_WALLET);
        assertThat(preview.getType()).isNull();
        assertThat(preview.getAmount()).isEqualByComparingTo(amount);
        assertThat(preview.isReadyToConfirm()).isFalse();
        assertThat(preview.getWarnings()).contains("VOICE_INTENT_NOT_COMMITTABLE");
        assertThat(preview.getCandidates()).hasSize(1);
        assertThat(preview.getCandidates().get(0).getIntentType()).isEqualTo(intentType);
    }

    private Fixture fixture() {
        Workspace workspace = Workspace.builder()
                .id(UUID.randomUUID())
                .timezone("Asia/Ho_Chi_Minh")
                .quickAmountUnit("THOUSAND")
                .build();
        Wallet cash = wallet("Tiền mặt", WalletType.CASH, true);
        Category food = category("Ăn uống", CategoryType.EXPENSE);
        Category gas = category("Xăng xe", CategoryType.EXPENSE);
        Category coffee = category("Cà phê", CategoryType.EXPENSE);
        Category salary = category("Thu nhập", CategoryType.INCOME);
        return new Fixture(
                workspace,
                List.of(cash),
                List.of(food, gas, coffee, salary),
                List.of(
                        keyword(workspace, food, "ăn", 10),
                        keyword(workspace, food, "uống nước", 10),
                        keyword(workspace, coffee, "uống cà phê", 10),
                        keyword(workspace, gas, "đổ xăng", 10),
                        keyword(workspace, salary, "kiếm được", 10)));
    }

    private Wallet wallet(String name, WalletType type, boolean isDefault) {
        return Wallet.builder()
                .id(UUID.randomUUID())
                .name(name)
                .walletType(type)
                .isDefault(isDefault)
                .isActive(true)
                .build();
    }

    private Category category(String name, CategoryType type) {
        return Category.builder()
                .id(UUID.randomUUID())
                .name(name)
                .categoryType(type)
                .isActive(true)
                .isArchived(false)
                .build();
    }

    private CategoryKeyword keyword(Workspace workspace, Category category, String value, int priority) {
        return CategoryKeyword.builder()
                .id(UUID.randomUUID())
                .workspace(workspace)
                .category(category)
                .keyword(value)
                .priority(priority)
                .build();
    }

    private record Fixture(
            Workspace workspace,
            List<Wallet> wallets,
            List<Category> categories,
            List<CategoryKeyword> keywords) {
    }
}
