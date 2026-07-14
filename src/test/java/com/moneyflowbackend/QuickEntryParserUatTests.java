package com.moneyflowbackend;

import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.quickentry.dto.QuickEntryPreviewResponse;
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
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QuickEntryParserUatTests {
    private final QuickEntryParser parser = new QuickEntryParser(
            new QuickAmountParser(),
            new QuickDateParser(Clock.fixed(Instant.parse("2026-07-09T05:00:00Z"), ZoneOffset.UTC)));

    @Test
    void parsesGroupedAmountsAndWorkspaceCategoryNames() {
        Fixture f = fixture();

        assertExpense(parser.parse("Tiền mua đồ ăn ngoài 27.000", f.workspace(), List.of(), f.categories(), f.wallets()), f.foodOut(), "27000");
        assertExpense(parser.parse("Tiền mua đồ ăn ngoài 27,000", f.workspace(), List.of(), f.categories(), f.wallets()), f.foodOut(), "27000");
        assertExpense(parser.parse("Tiền mua đồ ăn ngoài 27 000", f.workspace(), List.of(), f.categories(), f.wallets()), f.foodOut(), "27000");
        assertExpense(parser.parse("Tiền mua đồ ăn ngoài 27 nghìn", f.workspace(), List.of(), f.categories(), f.wallets()), f.foodOut(), "27000");
        assertExpense(parser.parse("Tiền mua đồ ăn ngoài 27 ngàn", f.workspace(), List.of(), f.categories(), f.wallets()), f.foodOut(), "27000");
    }

    @Test
    void ignoresTimeExpressionsWhenParsingAmounts() {
        Fixture f = fixture();

        var noon = parser.parse("Hôm nay 12 giờ trưa mua đồ ăn ngoài 27.000", f.workspace(), List.of(), f.categories(), f.wallets());
        assertExpense(noon, f.foodOut(), "27000");
        assertThat(noon.getTransactionTime()).isNull();

        assertExpense(parser.parse("12 giờ trưa mua đồ ăn ngoài 27k", f.workspace(), List.of(), f.categories(), f.wallets()), f.foodOut(), "27000");
        assertExpense(parser.parse("7 giờ tối mua đồ ăn ngoài 27k", f.workspace(), List.of(), f.categories(), f.wallets()), f.foodOut(), "27000");
        assertExpense(parser.parse("8 giờ sáng mua đồ ăn ngoài 27k", f.workspace(), List.of(), f.categories(), f.wallets()), f.foodOut(), "27000");

        var colon = parser.parse("12:30 mua đồ ăn ngoài 27k", f.workspace(), List.of(), f.categories(), f.wallets());
        assertExpense(colon, f.foodOut(), "27000");
        assertThat(colon.getTransactionTime()).isEqualTo(LocalTime.of(12, 30));
    }

    @Test
    void infersBuiltInAliasesOnlyWhenWorkspaceCategoryExists() {
        Fixture f = fixture();

        assertExpense(parser.parse("cafe 20k", f.workspace(), List.of(), f.categories(), f.wallets()), f.drinks(), "20000");
        assertExpense(parser.parse("xăng xe 50k", f.workspace(), List.of(), f.categories(), f.wallets()), f.gas(), "50000");
        assertExpense(parser.parse("gửi xe 5k", f.workspace(), List.of(), f.categories(), f.wallets()), f.parking(), "5000");
    }

    @Test
    void keepsExistingMultiAmountBehavior() {
        Fixture f = fixture();

        var preview = parser.parse("35k cafe 20k", f.workspace(), List.of(), f.categories(), f.wallets());

        assertThat(preview.getWarnings()).contains("MULTIPLE_ITEMS_DETECTED").doesNotContain("AMBIGUOUS_AMOUNT");
        assertThat(preview.getCandidates()).extracting("amount")
                .containsExactly(new BigDecimal("35000"), new BigDecimal("20000"));
    }

    private void assertExpense(QuickEntryPreviewResponse preview, Category category, String amount) {
        assertThat(preview.getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(preview.getAmount()).isEqualByComparingTo(amount);
        assertThat(preview.getCategoryId()).isEqualTo(category.getId());
        assertThat(preview.getCategoryName()).isEqualTo(category.getName());
        assertThat(preview.getCandidates()).isEmpty();
        assertThat(preview.getWarnings()).doesNotContain("AMBIGUOUS_AMOUNT");
    }

    private Fixture fixture() {
        Workspace workspace = Workspace.builder()
                .id(UUID.randomUUID())
                .timezone("Asia/Ho_Chi_Minh")
                .quickAmountUnit("THOUSAND")
                .build();
        Wallet cash = Wallet.builder()
                .id(UUID.randomUUID())
                .name("Tiền mặt")
                .walletType(WalletType.CASH)
                .isDefault(true)
                .isActive(true)
                .build();
        Category foodOut = category("Mua đồ ăn ngoài");
        Category drinks = category("Mua nước uống");
        Category gas = category("Xăng xe");
        Category parking = category("Gửi xe");
        return new Fixture(workspace, List.of(cash), List.of(foodOut, drinks, gas, parking), foodOut, drinks, gas, parking);
    }

    private Category category(String name) {
        return Category.builder()
                .id(UUID.randomUUID())
                .name(name)
                .categoryType(CategoryType.EXPENSE)
                .isActive(true)
                .isArchived(false)
                .build();
    }

    private record Fixture(
            Workspace workspace,
            List<Wallet> wallets,
            List<Category> categories,
            Category foodOut,
            Category drinks,
            Category gas,
            Category parking) {
    }
}
