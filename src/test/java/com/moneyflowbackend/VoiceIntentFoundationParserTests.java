package com.moneyflowbackend;

import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryKeyword;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.quickentry.dto.VoiceCandidateStatus;
import com.moneyflowbackend.quickentry.dto.VoiceIntentType;
import com.moneyflowbackend.quickentry.dto.VoiceLedgerEffect;
import com.moneyflowbackend.quickentry.dto.QuickEntryPreviewResponse;
import com.moneyflowbackend.quickentry.parser.QuickAmountParser;
import com.moneyflowbackend.quickentry.parser.QuickDateParser;
import com.moneyflowbackend.quickentry.parser.QuickEntryParser;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.model.WalletType;
import com.moneyflowbackend.workspace.model.Workspace;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VoiceIntentFoundationParserTests {
    private final QuickEntryParser parser = new QuickEntryParser(
            new QuickAmountParser(),
            new QuickDateParser(Clock.fixed(Instant.parse("2026-06-15T01:00:00Z"), ZoneOffset.UTC)));

    @Test
    void transactionIntentsKeepLegacyFields() {
        Fixture f = fixture();

        var expense = parser.parse("an sang 35k tien mat", f.workspace(), f.keywords(), f.categories(), f.wallets());
        var income = parser.parse("luong 5 trieu mb", f.workspace(), f.keywords(), f.categories(), f.wallets());
        var transfer = parser.parse("chuyen 200k tu MB Bank sang Tien mat", f.workspace(), f.keywords(), f.categories(), f.wallets());

        assertThat(expense.getIntentType()).isEqualTo(VoiceIntentType.TRANSACTION_EXPENSE);
        assertThat(expense.getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(expense.getCandidateId()).startsWith("cand_");
        assertThat(income.getIntentType()).isEqualTo(VoiceIntentType.TRANSACTION_INCOME);
        assertThat(transfer.getIntentType()).isEqualTo(VoiceIntentType.TRANSACTION_TRANSFER);
    }

    @Test
    void debtPaymentBecomesTypedDraftNotExpense() {
        Fixture f = fixture();

        var preview = parser.parse("tra no 500k hom qua", f.workspace(), f.keywords(), f.categories(), f.wallets());

        assertThat(preview.getIntentType()).isEqualTo(VoiceIntentType.DEBT_PAYMENT);
        assertThat(preview.getCandidateStatus()).isEqualTo(VoiceCandidateStatus.MANUAL);
        assertThat(preview.getType()).isNull();
        assertThat(preview.getAmount()).isEqualByComparingTo("500000");
        assertThat(preview.getTransactionDate()).isEqualTo(LocalDate.of(2026, 6, 14));
        assertThat(preview.getMissingFields()).contains("debtId");
        assertThat(preview.getWarnings()).contains("VOICE_INTENT_NOT_COMMITTABLE");
    }

    @Test
    void savingsSinkingEmergencyDraftsAreTypedAndNotCommittable() {
        Fixture f = fixture();

        assertThat(parser.parse("gop tiet kiem 1tr", f.workspace(), f.keywords(), f.categories(), f.wallets()).getIntentType())
                .isEqualTo(VoiceIntentType.SAVINGS_GOAL_CONTRIBUTION);
        assertThat(parser.parse("gop quy chim 1tr", f.workspace(), f.keywords(), f.categories(), f.wallets()).getIntentType())
                .isEqualTo(VoiceIntentType.SINKING_FUND_CONTRIBUTION);
        assertThat(parser.parse("quy khan cap 1tr", f.workspace(), f.keywords(), f.categories(), f.wallets()).getIntentType())
                .isEqualTo(VoiceIntentType.EMERGENCY_FUND_CONTRIBUTION);
    }

    @Test
    void phaseTwoVietnameseDraftExamplesStayDraftOnly() {
        Fixture f = fixture();

        assertDraft(parser.parse("cho Bảo mượn 750k", f.workspace(), f.keywords(), f.categories(), f.wallets()), VoiceIntentType.LOAN_DISBURSEMENT);
        assertDraft(parser.parse("Bảo trả tôi 180k", f.workspace(), f.keywords(), f.categories(), f.wallets()), VoiceIntentType.LOAN_COLLECTION);
        assertDraft(parser.parse("trả nợ 1 triệu cho chị Nga", f.workspace(), f.keywords(), f.categories(), f.wallets()), VoiceIntentType.DEBT_PAYMENT);
        assertDraft(parser.parse("góp 300k vào mục tiêu du lịch", f.workspace(), f.keywords(), f.categories(), f.wallets()), VoiceIntentType.SAVINGS_GOAL_CONTRIBUTION);
        assertDraft(parser.parse("góp 500k vào quỹ sửa xe", f.workspace(), f.keywords(), f.categories(), f.wallets()), VoiceIntentType.SINKING_FUND_CONTRIBUTION);
        assertDraft(parser.parse("ví tiền mặt còn 2 triệu", f.workspace(), f.keywords(), f.categories(), f.wallets()), VoiceIntentType.WALLET_BALANCE_SNAPSHOT);
        assertDraft(parser.parse("đã trả tiền điện tháng này", f.workspace(), f.keywords(), f.categories(), f.wallets()), VoiceIntentType.RECURRING_OBLIGATION_PAYMENT);
    }

    @Test
    void unknownUnsupportedIsNotCoercedToExpense() {
        Fixture f = fixture();

        var preview = parser.parse("xem bao cao thang nay", f.workspace(), f.keywords(), f.categories(), f.wallets());

        assertThat(preview.getIntentType()).isEqualTo(VoiceIntentType.ANALYTICS_QUERY);
        assertThat(preview.getCandidateStatus()).isEqualTo(VoiceCandidateStatus.READ_ONLY);
        assertThat(preview.getLedgerEffect()).isEqualTo(VoiceLedgerEffect.READ_ONLY);
        assertThat(preview.isReadyToConfirm()).isFalse();
        assertThat(preview.getWarnings()).contains("UNSUPPORTED_INTENT");
    }

    @Test
    void voiceUatMixedIncomeExpenseAndDebtInterestCandidatesKeepTheirIntent() {
        Fixture f = fixture();

        var preview = parser.parse("Hôm nay Đức kiếm được 800 ăn trưa 5 chục đổ xăng 60 uống cà phê 25.000 Tôi trả tiền lãi 75 ngàn",
                f.workspace(), f.keywords(), f.categories(), f.wallets());

        assertThat(preview.getCandidates()).hasSize(5);
        assertCandidate(preview.getCandidates().get(0), VoiceIntentType.TRANSACTION_INCOME, "800000");
        assertCandidate(preview.getCandidates().get(1), VoiceIntentType.TRANSACTION_EXPENSE, "50000");
        assertCandidate(preview.getCandidates().get(2), VoiceIntentType.TRANSACTION_EXPENSE, "60000");
        assertCandidate(preview.getCandidates().get(3), VoiceIntentType.TRANSACTION_EXPENSE, "25000");
        assertCandidate(preview.getCandidates().get(4), VoiceIntentType.INTEREST_EXPENSE, "75000");
        assertThat(preview.getCandidates().get(4).getCandidateStatus()).isEqualTo(VoiceCandidateStatus.MANUAL);
    }

    @Test
    void voiceUatSavingsCandidateIsNotExpense() {
        Fixture f = fixture();

        var preview = parser.parse("Hôm nay tôi kiếm được 800 gửi tiết kiệm 25 ăn trưa 50 đổ xăng 60",
                f.workspace(), f.keywords(), f.categories(), f.wallets());

        assertThat(preview.getCandidates()).hasSize(4);
        assertCandidate(preview.getCandidates().get(0), VoiceIntentType.TRANSACTION_INCOME, "800000");
        assertCandidate(preview.getCandidates().get(1), VoiceIntentType.SAVINGS_GOAL_CONTRIBUTION, "25000");
        assertCandidate(preview.getCandidates().get(2), VoiceIntentType.TRANSACTION_EXPENSE, "50000");
        assertCandidate(preview.getCandidates().get(3), VoiceIntentType.TRANSACTION_EXPENSE, "60000");
        assertThat(preview.getCandidates().get(1).getType()).isNull();
    }

    @Test
    void voiceUatSingleDomainCommandsAreDraftOnly() {
        Fixture f = fixture();

        var debt = parser.parse("Bảo nợ tôi 500k", f.workspace(), f.keywords(), f.categories(), f.wallets());
        var wallet = parser.parse("ví tiền mặt của anh còn 2 triệu", f.workspace(), f.keywords(), f.categories(), f.wallets());
        var stat = parser.parse("tháng này tôi tiêu bao nhiêu", f.workspace(), f.keywords(), f.categories(), f.wallets());

        assertThat(debt.getIntentType()).isEqualTo(VoiceIntentType.DEBT_CREATE_RECEIVABLE);
        assertThat(debt.getAmount()).isEqualByComparingTo("500000");
        assertThat(wallet.getIntentType()).isEqualTo(VoiceIntentType.WALLET_BALANCE_SNAPSHOT);
        assertThat(wallet.getAmount()).isEqualByComparingTo("2000000");
        assertThat(stat.getIntentType()).isEqualTo(VoiceIntentType.STAT_QUERY);
        assertThat(stat.getAmount()).isNull();
        assertThat(List.of(debt, wallet, stat)).allSatisfy(item -> {
            assertThat(item.getType()).isNull();
            assertThat(item.isReadyToConfirm()).isFalse();
        });
    }

    private Fixture fixture() {
        Workspace workspace = Workspace.builder()
                .id(UUID.randomUUID())
                .timezone("Asia/Ho_Chi_Minh")
                .quickAmountUnit("THOUSAND")
                .build();
        Wallet cash = wallet("Tien mat", WalletType.CASH, true);
        Wallet bank = wallet("MB Bank", WalletType.BANK, false);
        Category food = category("Food", CategoryType.EXPENSE);
        Category salary = category("Salary", CategoryType.INCOME);
        List<CategoryKeyword> keywords = List.of(
                keyword(workspace, food, "an sang", 10),
                keyword(workspace, salary, "luong", 10));
        return new Fixture(workspace, List.of(cash, bank), List.of(food, salary), keywords);
    }

    private void assertDraft(QuickEntryPreviewResponse preview, VoiceIntentType intentType) {
        assertThat(preview.getIntentType()).isEqualTo(intentType);
        assertThat(preview.getCandidateStatus()).isEqualTo(VoiceCandidateStatus.MANUAL);
        assertThat(preview.getLedgerEffect()).isIn(VoiceLedgerEffect.MANUAL_UNSUPPORTED, VoiceLedgerEffect.DOES_NOT_AFFECT_WALLET);
        assertThat(preview.isReadyToConfirm()).isFalse();
        assertThat(preview.getType()).isNull();
        assertThat(preview.getSuggestedManualRoute()).isNotBlank();
        assertThat(preview.getWarnings()).contains("VOICE_INTENT_NOT_COMMITTABLE");
    }

    private void assertCandidate(QuickEntryPreviewResponse.Candidate candidate, VoiceIntentType intentType, String amount) {
        assertThat(candidate.getIntentType()).isEqualTo(intentType);
        assertThat(candidate.getAmount()).isEqualByComparingTo(amount);
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
