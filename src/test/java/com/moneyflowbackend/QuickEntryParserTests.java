package com.moneyflowbackend;

import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryKeyword;
import com.moneyflowbackend.category.model.CategoryType;
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

class QuickEntryParserTests {
    private final QuickEntryParser parser = new QuickEntryParser(
            new QuickAmountParser(),
            new QuickDateParser(Clock.fixed(Instant.parse("2026-06-15T01:00:00Z"), ZoneOffset.UTC)));

    @Test
    void parsesReadyExpenseWithKeywordWalletAndDefaultDate() {
        Fixture f = fixture();
        var preview = parser.parse("an sang 35k tien mat", f.workspace(), f.keywords(), f.categories(), f.wallets());

        assertThat(preview.isReadyToConfirm()).isTrue();
        assertThat(preview.getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(preview.getAmount()).isEqualByComparingTo("35000");
        assertThat(preview.getWalletId()).isEqualTo(f.cash().getId());
        assertThat(preview.getCategoryId()).isEqualTo(f.food().getId());
        assertThat(preview.getTransactionDate()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(preview.getDescription()).isEqualTo("An sang");
        assertThat(preview.getWarnings()).isEmpty();
    }

    @Test
    void longestKeywordWinsAndDefaultWalletWarns() {
        Fixture f = fixture();
        var preview = parser.parse("an sang 35k", f.workspace(), f.keywords(), f.categories(), f.wallets());

        assertThat(preview.getCategoryId()).isEqualTo(f.food().getId());
        assertThat(preview.getMatchedKeyword()).isEqualTo("an sang");
        assertThat(preview.getWalletId()).isEqualTo(f.cash().getId());
        assertThat(preview.getWarnings()).contains("DEFAULT_WALLET_USED");
    }

    @Test
    void parsesIncomeAndWalletAcronym() {
        Fixture f = fixture();
        var preview = parser.parse("luong 5 trieu mb", f.workspace(), f.keywords(), f.categories(), f.wallets());

        assertThat(preview.getType()).isEqualTo(TransactionType.INCOME);
        assertThat(preview.getAmount()).isEqualByComparingTo("5000000");
        assertThat(preview.getWalletId()).isEqualTo(f.bank().getId());
        assertThat(preview.getCategoryId()).isEqualTo(f.salary().getId());
    }

    @Test
    void parsesVietnameseAmountDateAndIntentCorpus() {
        Fixture f = fixture();
        var expense = parser.parse("hôm qua cà phê 1,5tr tiền mặt", f.workspace(),
                List.of(keyword(f.workspace(), f.food(), "cà phê", 20)), f.categories(), f.wallets());
        var income = parser.parse("mẹ cho 500k mb", f.workspace(),
                List.of(keyword(f.workspace(), f.salary(), "mẹ cho", 20)), f.categories(), f.wallets());

        assertThat(expense.getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(expense.getAmount()).isEqualByComparingTo("1500000");
        assertThat(expense.getTransactionDate()).isEqualTo(LocalDate.of(2026, 6, 14));
        assertThat(income.getType()).isEqualTo(TransactionType.INCOME);
        assertThat(income.getAmount()).isEqualByComparingTo("500000");
    }

    @Test
    void suggestedWalletBeatsDefaultWhenWalletIsUnclear() {
        Fixture f = fixture();
        var preview = parser.parse("an sang 35k", f.workspace(), f.keywords(), f.categories(), f.wallets(), f.bank().getId());

        assertThat(preview.getWalletId()).isEqualTo(f.bank().getId());
        assertThat(preview.getWarnings()).contains("SUGGESTED_WALLET_USED");
        assertThat(preview.isReadyToConfirm()).isTrue();
    }

    @Test
    void conflictingIntentAndCategoryTypeDoesNotGuess() {
        Fixture f = fixture();
        var preview = parser.parse("lương ăn sáng 35k", f.workspace(), f.keywords(), f.categories(), f.wallets());

        assertThat(preview.getType()).isNull();
        assertThat(preview.getCategoryId()).isNull();
        assertThat(preview.getWarnings()).contains("CATEGORY_TYPE_MISMATCH");
        assertThat(preview.getMissingFields()).contains("TYPE", "CATEGORY");
        assertThat(preview.isReadyToConfirm()).isFalse();
    }

    @Test
    void parsesTransferWalletsAndRejectsSameWallet() {
        Fixture f = fixture();
        var preview = parser.parse("chuyen 500k tu MB Bank sang Tien mat", f.workspace(), f.keywords(), f.categories(), f.wallets());

        assertThat(preview.getType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(preview.getSourceWalletId()).isEqualTo(f.bank().getId());
        assertThat(preview.getDestinationWalletId()).isEqualTo(f.cash().getId());
        assertThat(preview.getCategoryId()).isNull();
        assertThat(preview.isReadyToConfirm()).isTrue();

        var same = parser.parse("chuyen 500k tu MB Bank sang MB Bank", f.workspace(), f.keywords(), f.categories(), f.wallets());
        assertThat(same.isReadyToConfirm()).isFalse();
        assertThat(same.getWarnings()).contains("TRANSFER_SAME_WALLET");
    }

    @Test
    void parsesVoiceUatPhrasesWithSameQuickTextParser() {
        Fixture f = fixture();
        Wallet momo = wallet("MoMo", WalletType.E_WALLET, false);
        List<Wallet> wallets = List.of(f.cash(), f.bank(), momo);
        List<CategoryKeyword> keywords = List.of(
                keyword(f.workspace(), f.food(), "ăn sáng", 20),
                keyword(f.workspace(), f.salary(), "mẹ cho", 20));

        var expense = parser.parse("ăn sáng 35k tiền mặt", f.workspace(), keywords, f.categories(), wallets);
        var income = parser.parse("mẹ cho 500k vào MB", f.workspace(), keywords, f.categories(), wallets);
        var transfer = parser.parse("chuyển 200k từ MB sang MoMo", f.workspace(), keywords, f.categories(), wallets);

        assertThat(expense.isReadyToConfirm()).isTrue();
        assertThat(expense.getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(expense.getAmount()).isEqualByComparingTo("35000");
        assertThat(expense.getWalletId()).isEqualTo(f.cash().getId());
        assertThat(expense.getCategoryId()).isEqualTo(f.food().getId());

        assertThat(income.isReadyToConfirm()).isTrue();
        assertThat(income.getType()).isEqualTo(TransactionType.INCOME);
        assertThat(income.getAmount()).isEqualByComparingTo("500000");
        assertThat(income.getWalletId()).isEqualTo(f.bank().getId());
        assertThat(income.getCategoryId()).isEqualTo(f.salary().getId());

        assertThat(transfer.isReadyToConfirm()).isTrue();
        assertThat(transfer.getType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(transfer.getAmount()).isEqualByComparingTo("200000");
        assertThat(transfer.getSourceWalletId()).isEqualTo(f.bank().getId());
        assertThat(transfer.getDestinationWalletId()).isEqualTo(momo.getId());
    }

    @Test
    void detectsNaturalMultiExpenseTranscriptAndPrefillsFirstAmount() {
        Fixture f = fixture();
        var preview = parser.parse("Hôm nay tiền ăn 15.000 Uống cà phê 15.000", f.workspace(), List.of(), f.categories(), f.wallets());

        assertThat(preview.getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(preview.getAmount()).isEqualByComparingTo("15000");
        assertThat(preview.getMissingFields()).doesNotContain("AMOUNT");
        assertThat(preview.getWarnings()).contains("MULTIPLE_ITEMS_DETECTED").doesNotContain("AMBIGUOUS_AMOUNT");
        assertThat(preview.getCandidates()).hasSize(2);
        assertThat(preview.getCandidates()).extracting("candidateId").doesNotContainNull();
        assertThat(preview.getCandidates()).extracting("transactionDate")
                .containsExactly(preview.getTransactionDate(), preview.getTransactionDate());
        assertThat(preview.getCandidates()).extracting("amount")
                .containsExactly(new java.math.BigDecimal("15000"), new java.math.BigDecimal("15000"));
        assertThat(preview.getCandidates()).extracting("description")
                .containsExactly("Tiền ăn", "Uống cà phê");
    }

    @Test
    void detectsNaturalMultiExpenseAmountsWithKAndBareNumbers() {
        Fixture f = fixture();
        var breakfast = parser.parse("ăn sáng 35k cafe 20k", f.workspace(), List.of(), f.categories(), f.wallets());
        var food = parser.parse("tiền ăn 15000 uống nước 12000", f.workspace(), List.of(), f.categories(), f.wallets());

        assertThat(breakfast.getCandidates()).extracting("amount")
                .containsExactly(new java.math.BigDecimal("35000"), new java.math.BigDecimal("20000"));
        assertThat(food.getCandidates()).extracting("amount")
                .containsExactly(new java.math.BigDecimal("15000"), new java.math.BigDecimal("12000"));
        assertThat(breakfast.getMissingFields()).doesNotContain("AMOUNT");
        assertThat(food.getMissingFields()).doesNotContain("AMOUNT");
    }

    @Test
    void detectsTransportMultiExpenseAmounts() {
        Fixture f = fixture();
        Category transport = category("Transport", CategoryType.EXPENSE);
        var preview = parser.parse("xăng xe 50k gửi xe 5k", f.workspace(), List.of(), List.of(f.food(), f.salary(), transport), f.wallets());

        assertThat(preview.getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(preview.getCandidates()).extracting("amount")
                .containsExactly(new java.math.BigDecimal("50000"), new java.math.BigDecimal("5000"));
        assertThat(preview.getCandidates()).extracting("categoryName")
                .containsExactly("Transport", "Transport");
        assertThat(preview.getCandidates()).extracting("validationStatus")
                .containsExactly("READY", "READY");
    }

    @Test
    void invalidTextDoesNotThrowAndDateIsNotAmount() {
        Fixture f = fixture();
        var invalid = parser.parse("s", f.workspace(), f.keywords(), f.categories(), f.wallets());
        var dated = parser.parse("ngày 03/07/2026 ăn sáng 35k", f.workspace(), f.keywords(), f.categories(), f.wallets());

        assertThat(invalid.getAmount()).isNull();
        assertThat(invalid.getMissingFields()).contains("AMOUNT");
        assertThat(dated.getAmount()).isEqualByComparingTo("35000");
        assertThat(dated.getCandidates()).isEmpty();
    }

    @Test
    void reportsAmbiguousCategoryAndMissingFields() {
        Fixture f = fixture();
        Category otherFood = category("Other Food", CategoryType.EXPENSE);
        CategoryKeyword duplicate = keyword(f.workspace(), otherFood, "cafe", 5);
        var preview = parser.parse("cafe 45k", f.workspace(), List.of(keyword(f.workspace(), f.food(), "cafe", 5), duplicate), List.of(f.food(), otherFood, f.salary()), f.wallets());

        assertThat(preview.isReadyToConfirm()).isFalse();
        assertThat(preview.getWarnings()).contains("AMBIGUOUS_CATEGORY", "DEFAULT_WALLET_USED");
        assertThat(preview.getMissingFields()).contains("CATEGORY");
    }

    @Test
    void reportsUnknownWalletWhenWalletMarkerIsPresent() {
        Fixture f = fixture();
        var preview = parser.parse("an sang 35k vi khong ton tai", f.workspace(), f.keywords(), f.categories(), f.wallets());

        assertThat(preview.isReadyToConfirm()).isFalse();
        assertThat(preview.getWarnings()).contains("UNKNOWN_WALLET");
        assertThat(preview.getMissingFields()).contains("WALLET");
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
                keyword(workspace, food, "an", 1),
                keyword(workspace, food, "an sang", 1),
                keyword(workspace, salary, "luong", 10));
        return new Fixture(workspace, cash, bank, food, salary, List.of(cash, bank), List.of(food, salary), keywords);
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
            Wallet cash,
            Wallet bank,
            Category food,
            Category salary,
            List<Wallet> wallets,
            List<Category> categories,
            List<CategoryKeyword> keywords) {
    }
}
