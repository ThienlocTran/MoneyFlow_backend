package com.moneyflowbackend;

import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryKeyword;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.quickentry.dto.VoiceCandidateStatus;
import com.moneyflowbackend.quickentry.dto.VoiceIntentType;
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
        assertThat(preview.getCandidateStatus()).isEqualTo(VoiceCandidateStatus.UNSUPPORTED);
        assertThat(preview.getType()).isNull();
        assertThat(preview.getAmount()).isEqualByComparingTo("500000");
        assertThat(preview.getTransactionDate()).isEqualTo(LocalDate.of(2026, 6, 14));
        assertThat(preview.getMissingFields()).contains("DEBT");
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
    void unknownUnsupportedIsNotCoercedToExpense() {
        Fixture f = fixture();

        var preview = parser.parse("xem bao cao thang nay", f.workspace(), f.keywords(), f.categories(), f.wallets());

        assertThat(preview.getIntentType()).isEqualTo(VoiceIntentType.UNKNOWN_UNSUPPORTED);
        assertThat(preview.getCandidateStatus()).isEqualTo(VoiceCandidateStatus.UNSUPPORTED);
        assertThat(preview.isReadyToConfirm()).isFalse();
        assertThat(preview.getWarnings()).contains("UNSUPPORTED_INTENT");
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
