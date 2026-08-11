package com.moneyflowbackend;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryKeyword;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.category.repository.CategoryKeywordRepository;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.receipt.dto.ReceiptReviewParseRequest;
import com.moneyflowbackend.receipt.dto.ReceiptReviewParseResponse;
import com.moneyflowbackend.receipt.dto.ReceiptReviewSource;
import com.moneyflowbackend.receipt.service.ReceiptReviewService;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.transaction.model.Transaction;
import com.moneyflowbackend.transaction.model.TransactionStatus;
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
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReceiptReviewIntegrationTests {
    @Autowired ReceiptReviewService receiptReviewService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired CategoryKeywordRepository categoryKeywordRepository;
    @Autowired TransactionRepository transactionRepository;

    @Test
    void parseReceiptReturnsReviewDraftWithoutCommitting() {
        TestContext ctx = context("receipt_review_parse", WorkspaceRole.OWNER);
        Wallet cash = wallet(ctx, "Cash");
        Category food = category(ctx, "An uong");
        keyword(ctx, food, "cafe");
        long before = transactionRepository.count();

        ReceiptReviewParseResponse response = receiptReviewService.parse(ctx.workspace().getId(), request("""
                Highlands Coffee
                Ngay 01/08/2026
                Cafe sua da 40.000
                Tong cong 40.000
                """, cash.getId()), ctx.user().getId());

        assertThat(response.getMode()).isEqualTo("RECEIPT_REVIEW");
        assertThat(response.getStatus()).isEqualTo("NEEDS_REVIEW");
        assertThat(response.getSource()).isEqualTo(ReceiptReviewSource.MANUAL_TEXT);
        assertThat(response.getCandidate().getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(response.getCandidate().getAmount()).isEqualByComparingTo("40000");
        assertThat(response.getCandidate().getCurrency()).isEqualTo("VND");
        assertThat(response.getCandidate().getWalletId()).isEqualTo(cash.getId());
        assertThat(response.getCandidate().getCategoryId()).isEqualTo(food.getId());
        assertThat(response.getCandidate().getMerchantName()).isEqualTo("Highlands Coffee");
        assertThat(response.getCandidate().getOccurredAt().toLocalDate()).hasToString("2026-08-01");
        assertThat(response.getCandidate().getNeedsFields()).isEmpty();
        assertThat(response.getExtracted().getLineAmounts()).contains(new BigDecimal("40000"));
        assertThat(transactionRepository.count()).isEqualTo(before);
    }

    @Test
    void amountFormatsAndUnlabeledTotalsNeedReview() {
        TestContext ctx = context("receipt_review_amounts", WorkspaceRole.OWNER);

        ReceiptReviewParseResponse comma = receiptReviewService.parse(ctx.workspace().getId(), request("Shop\nTotal 1,250,000", null), ctx.user().getId());
        ReceiptReviewParseResponse kilo = receiptReviewService.parse(ctx.workspace().getId(), request("Cafe\nTra sua 40k", null), ctx.user().getId());

        assertThat(comma.getCandidate().getAmount()).isEqualByComparingTo("1250000");
        assertThat(kilo.getStatus()).isEqualTo("UNSUPPORTED");
        assertThat(kilo.getWarnings()).extracting("code").contains("RECEIPT_TOTAL_NOT_FOUND");
    }

    @Test
    void shortTextIsUnsupportedDraft() {
        TestContext ctx = context("receipt_review_short", WorkspaceRole.OWNER);

        ReceiptReviewParseResponse response = receiptReviewService.parse(ctx.workspace().getId(), request("  ", null), ctx.user().getId());

        assertThat(response.getStatus()).isEqualTo("UNSUPPORTED");
        assertThat(response.getCandidate()).isNull();
        assertThat(response.getWarnings()).extracting("code").contains("RECEIPT_TEXT_TOO_SHORT", "RECEIPT_UNSUPPORTED_TEXT");
    }

    @Test
    void missingWalletAndCategoryAreNeedsFieldsNotFakeData() {
        TestContext ctx = context("receipt_review_needs", WorkspaceRole.OWNER);

        ReceiptReviewParseResponse response = receiptReviewService.parse(ctx.workspace().getId(), request("Unknown Merchant\nTotal 40000", null), ctx.user().getId());

        assertThat(response.getCandidate().getWalletId()).isNull();
        assertThat(response.getCandidate().getCategoryId()).isNull();
        assertThat(response.getCandidate().getNeedsFields()).contains("walletId", "categoryId");
        assertThat(response.getWarnings()).extracting("code").contains("RECEIPT_WALLET_NOT_SELECTED", "RECEIPT_CATEGORY_NOT_SELECTED");
    }

    @Test
    void bachHoaXanhReceiptUsesPayableTotalAndDoesNotGuessShipperCategory() {
        TestContext ctx = context("receipt_review_bhx", WorkspaceRole.OWNER);
        category(ctx, "Dua/nhan do cho shipper");
        category(ctx, "An uong");

        ReceiptReviewParseResponse response = receiptReviewService.parse(ctx.workspace().getId(), request("""
                PHIEU THANH TOAN BACH HOA XANH
                30/07/2026 16:29 G
                Phai thanh toan: 67.463
                Diem su dung: 2.463
                Tien mat (Da lam tron): 65.000
                Tien khach dua: 200.000
                Tien thoi lai: 135.000
                Ma tra cuu: 6359148EDC
                So CT: 18001067
                """, null), ctx.user().getId());

        assertThat(response.getCandidate().getAmount()).isEqualByComparingTo("67463");
        assertThat(response.getCandidate().getMerchantName()).isEqualTo("Bách Hóa Xanh");
        assertThat(response.getCandidate().getOccurredAt().toLocalDate()).hasToString("2026-07-30");
        assertThat(response.getCandidate().getCategoryName()).isNotEqualTo("Dua/nhan do cho shipper");
        assertThat(response.getExtracted().getLineAmounts()).doesNotContain(new BigDecimal("6359148"), new BigDecimal("18001067"), new BigDecimal("200000"), new BigDecimal("135000"), new BigDecimal("2463"));
        assertThat(response.getExtracted().getAmountCandidates()).anySatisfy(candidate -> {
            assertThat(candidate.getValue()).isEqualByComparingTo("65000");
            assertThat(candidate.isExcluded()).isFalse();
        });
        assertThat(response.getExtracted().getAmountCandidates()).anySatisfy(candidate -> {
            assertThat(candidate.getValue()).isEqualByComparingTo("6359148");
            assertThat(candidate.getExcludedReason()).isEqualTo("RECEIPT_CODE");
        });
        assertThat(response.getExtracted().getMerchantConfidence()).isIn("HIGH", "MEDIUM");
        assertThat(response.getExtracted().getDateConfidence()).isIn("HIGH", "MEDIUM");
    }

    @Test
    void groceryReceiptDoesNotSelectShipperCategoryWithoutDeliveryEvidence() {
        TestContext ctx = context("rr_no_shipper", WorkspaceRole.OWNER);
        category(ctx, "Dua/nhan do cho shipper");

        ReceiptReviewParseResponse response = receiptReviewService.parse(ctx.workspace().getId(), request("""
                PHIEU THANH TOAN BACH HOA XANH
                Phai thanh toan: 67.463
                """, null), ctx.user().getId());

        assertThat(response.getCandidate().getCategoryId()).isNull();
        assertThat(response.getWarnings()).extracting("code").contains("CATEGORY_SHIPPER_MATCH_REJECTED", "RECEIPT_CATEGORY_NOT_SELECTED");
    }

    @Test
    void uniqueGroceryCategoryIsSelected() {
        TestContext ctx = context("rr_grocery_unique", WorkspaceRole.OWNER);
        Category grocery = category(ctx, "Di cho");

        ReceiptReviewParseResponse response = receiptReviewService.parse(ctx.workspace().getId(), request("""
                PHIEU THANH TOAN BACH HOA XANH
                Phai thanh toan: 67.463
                """, null), ctx.user().getId());

        assertThat(response.getCandidate().getCategoryId()).isEqualTo(grocery.getId());
        assertThat(response.getExtracted().getCategoryConfidence()).isEqualTo("HIGH");
        assertThat(response.getWarnings()).extracting("code").contains("CATEGORY_MERCHANT_RULE_MATCH_USED");
    }

    @Test
    void ambiguousGroceryCategoriesAreNotGuessed() {
        TestContext ctx = context("rr_grocery_amb", WorkspaceRole.OWNER);
        category(ctx, "Di cho");
        category(ctx, "An uong");

        ReceiptReviewParseResponse response = receiptReviewService.parse(ctx.workspace().getId(), request("""
                PHIEU THANH TOAN BACH HOA XANH
                Phai thanh toan: 67.463
                """, null), ctx.user().getId());

        assertThat(response.getCandidate().getCategoryId()).isNull();
        assertThat(response.getWarnings()).extracting("code").contains("CATEGORY_AMBIGUOUS_MATCH");
    }

    @Test
    void merchantHistoryWinsCategorySuggestion() {
        TestContext ctx = context("receipt_review_history", WorkspaceRole.OWNER);
        Category grocery = category(ctx, "Di cho");
        category(ctx, "An uong");
        transaction(ctx, grocery, "Bách Hóa Xanh");
        transaction(ctx, grocery, "Bách Hóa Xanh");
        transaction(ctx, grocery, "Bách Hóa Xanh");

        ReceiptReviewParseResponse response = receiptReviewService.parse(ctx.workspace().getId(), request("""
                PHIEU THANH TOAN BACH HOA XANH
                Phai thanh toan: 67.463
                """, null), ctx.user().getId());

        assertThat(response.getCandidate().getCategoryId()).isEqualTo(grocery.getId());
        assertThat(response.getWarnings()).extracting("code").contains("CATEGORY_HISTORY_MATCH_USED");
    }

    @Test
    void archivedAndCrossWorkspaceCategoriesAreIgnored() {
        TestContext ctx = context("receipt_review_archived", WorkspaceRole.OWNER);
        TestContext other = context("rr_archived_other", WorkspaceRole.OWNER);
        category(ctx, "Di cho", true);
        Category otherGrocery = category(other, "Di cho");
        transaction(other, otherGrocery, "Bách Hóa Xanh");

        ReceiptReviewParseResponse response = receiptReviewService.parse(ctx.workspace().getId(), request("""
                PHIEU THANH TOAN BACH HOA XANH
                Phai thanh toan: 67.463
                """, null), ctx.user().getId());

        assertThat(response.getCandidate().getCategoryId()).isNull();
        assertThat(response.getWarnings()).extracting("code").contains("CATEGORY_NO_ACTIVE_MATCH");
    }

    @Test
    void payableTotalBeatsLargestCustomerCashAmounts() {
        TestContext ctx = context("receipt_review_total_rank", WorkspaceRole.OWNER);

        ReceiptReviewParseResponse response = receiptReviewService.parse(ctx.workspace().getId(), request("""
                MINI MART
                Tien khach dua: 500.000
                Tien thoi lai: 430.000
                Phai thanh toan: 70.000
                """, null), ctx.user().getId());

        assertThat(response.getCandidate().getAmount()).isEqualByComparingTo("70000");
        assertThat(response.getExtracted().getLineAmounts()).doesNotContain(new BigDecimal("500000"), new BigDecimal("430000"));
    }

    @Test
    void occurredAtHintIsUsedWhenReceiptDateMissing() {
        TestContext ctx = context("receipt_review_hint", WorkspaceRole.OWNER);
        ReceiptReviewParseRequest req = request("Cafe\nTotal 40000", null);
        req.setOccurredAtHint(OffsetDateTime.parse("2026-07-30T12:00:00+07:00"));

        ReceiptReviewParseResponse response = receiptReviewService.parse(ctx.workspace().getId(), req, ctx.user().getId());

        assertThat(response.getCandidate().getOccurredAt().toLocalDate()).hasToString("2026-07-30");
        assertThat(response.getWarnings()).extracting("code").doesNotContain("RECEIPT_DATE_INFERRED");
    }

    @Test
    void crossWorkspaceWalletIsRejected() {
        TestContext ctx = context("receipt_review_refs", WorkspaceRole.OWNER);
        TestContext other = context("receipt_review_refs_other", WorkspaceRole.OWNER);
        Wallet otherCash = wallet(other, "Cash");

        assertBusinessCode(() -> receiptReviewService.parse(ctx.workspace().getId(), request("Cafe\nTotal 40000", otherCash.getId()), ctx.user().getId()),
                "WALLET_NOT_FOUND");
    }

    @Test
    void nonMemberCannotParseWorkspaceReceipt() {
        TestContext ctx = context("receipt_review_auth", WorkspaceRole.OWNER);
        TestContext other = context("receipt_review_auth_other", WorkspaceRole.OWNER);

        assertBusinessCode(() -> receiptReviewService.parse(ctx.workspace().getId(), request("Cafe\nTotal 40000", null), other.user().getId()),
                "WORKSPACE_ACCESS_DENIED");
    }

    private ReceiptReviewParseRequest request(String rawText, UUID walletId) {
        ReceiptReviewParseRequest req = new ReceiptReviewParseRequest();
        req.setRawText(rawText);
        req.setWalletId(walletId);
        return req;
    }

    private TestContext context(String prefix, WorkspaceRole role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .fullName("Receipt Review Test User")
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

    private Category category(TestContext ctx, String name) {
        return category(ctx, name, false);
    }

    private Category category(TestContext ctx, String name, boolean archived) {
        return categoryRepository.saveAndFlush(Category.builder()
                .workspace(ctx.workspace())
                .name(name)
                .categoryType(CategoryType.EXPENSE)
                .isActive(!archived)
                .isArchived(archived)
                .build());
    }

    private void transaction(TestContext ctx, Category category, String merchant) {
        transactionRepository.saveAndFlush(Transaction.builder()
                .workspace(ctx.workspace())
                .createdByUser(ctx.user())
                .category(category)
                .transactionType(TransactionType.EXPENSE)
                .transactionStatus(TransactionStatus.POSTED)
                .amount(new BigDecimal("10000"))
                .currency("VND")
                .transactionDate(java.time.LocalDate.of(2026, 7, 1))
                .description(merchant)
                .note(merchant)
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

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }

    private record TestContext(User user, Workspace workspace) {}
}
