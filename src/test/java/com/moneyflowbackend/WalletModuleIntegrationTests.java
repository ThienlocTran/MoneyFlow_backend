package com.moneyflowbackend;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.transaction.model.Transaction;
import com.moneyflowbackend.transaction.model.TransactionStatus;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.transaction.repository.TransferDetailRepository;
import com.moneyflowbackend.wallet.dto.WalletRequest;
import com.moneyflowbackend.wallet.dto.WalletResponse;
import com.moneyflowbackend.wallet.dto.WalletSummaryResponse;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.model.WalletType;
import com.moneyflowbackend.wallet.repository.WalletRepository;
import com.moneyflowbackend.wallet.service.WalletService;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WalletModuleIntegrationTests {

    @Autowired WalletService walletService;
    @Autowired WalletRepository walletRepository;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired TransferDetailRepository transferDetailRepository;
    @Autowired EntityManager entityManager;

    @Test
    void createFirstWalletAutoDefaultAndOpeningDateRequiredWhenBalanceNonZero() {
        TestContext ctx = createContext("first_wallet", WorkspaceRole.OWNER);

        assertThatThrownBy(() -> walletService.create(ctx.workspace().getId(), walletRequest("Cash", WalletType.CASH, bd("100"), null, false, true), ctx.user().getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("VALIDATION_ERROR");

        WalletResponse first = walletService.create(ctx.workspace().getId(), walletRequest("Cash", WalletType.CASH, BigDecimal.ZERO, null, false, true), ctx.user().getId());

        assertThat(first.isDefault()).isTrue();
        assertThat(first.isActive()).isTrue();
        assertThat(first.isIncludeInTotal()).isTrue();
        assertThat(walletRepository.findByWorkspaceIdAndIsDefaultTrueAndIsActiveTrue(ctx.workspace().getId())).isPresent();

        WalletRequest invalidType = walletRequest("Crypto", WalletType.CASH, BigDecimal.ZERO, null, false, true);
        invalidType.setWalletType("CRYPTO");
        assertThatThrownBy(() -> walletService.create(ctx.workspace().getId(), invalidType, ctx.user().getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("INVALID_WALLET_TYPE");
    }

    @Test
    void updateDefaultDuplicateAndStatusRulesWork() {
        TestContext ctx = createContext("wallet_rules", WorkspaceRole.OWNER);
        WalletResponse cash = walletService.create(ctx.workspace().getId(), walletRequest("Cash", WalletType.CASH, BigDecimal.ZERO, null, false, true), ctx.user().getId());
        WalletResponse bank = walletService.create(ctx.workspace().getId(), walletRequest("MB Bank", WalletType.BANK, BigDecimal.ZERO, null, true, true), ctx.user().getId());

        assertThat(walletRepository.findById(cash.getId()).orElseThrow().isDefault()).isFalse();
        assertThat(walletRepository.findById(bank.getId()).orElseThrow().isDefault()).isTrue();

        assertThatThrownBy(() -> walletService.create(ctx.workspace().getId(), walletRequest("mb bank", WalletType.BANK, BigDecimal.ZERO, null, false, true), ctx.user().getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("WALLET_NAME_ALREADY_EXISTS");

        WalletResponse updated = walletService.update(ctx.workspace().getId(), cash.getId(), walletRequest("Cash Box", WalletType.OTHER, bd("10"), LocalDate.of(2026, 6, 1), false, false), ctx.user().getId());
        assertThat(updated.getName()).isEqualTo("Cash Box");
        assertThat(updated.getType()).isEqualTo("OTHER");
        assertThat(updated.getOpeningBalance()).isEqualByComparingTo("10");
        assertThat(updated.getOpeningDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(updated.isIncludeInTotal()).isFalse();

        assertThatThrownBy(() -> walletService.update(ctx.workspace().getId(), cash.getId(), walletRequest("MB BANK", WalletType.CASH, BigDecimal.ZERO, null, false, true), ctx.user().getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("WALLET_NAME_ALREADY_EXISTS");

        walletService.setDefault(ctx.workspace().getId(), cash.getId(), ctx.user().getId());
        assertThat(walletRepository.findById(cash.getId()).orElseThrow().isDefault()).isTrue();
        assertThat(walletRepository.findById(bank.getId()).orElseThrow().isDefault()).isFalse();

        assertThatThrownBy(() -> walletService.toggleStatus(ctx.workspace().getId(), cash.getId(), false, ctx.user().getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("DEFAULT_WALLET_CANNOT_BE_DISABLED");

        walletService.toggleStatus(ctx.workspace().getId(), bank.getId(), false, ctx.user().getId());
        assertThat(walletRepository.findById(bank.getId()).orElseThrow().isActive()).isFalse();
        assertThatThrownBy(() -> walletService.setDefault(ctx.workspace().getId(), bank.getId(), ctx.user().getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("WALLET_INACTIVE");

        walletService.toggleStatus(ctx.workspace().getId(), bank.getId(), true, ctx.user().getId());
        walletService.setDefault(ctx.workspace().getId(), bank.getId(), ctx.user().getId());
        assertThat(walletRepository.findByWorkspaceIdAndIsDefaultTrueAndIsActiveTrue(ctx.workspace().getId()).orElseThrow().getId()).isEqualTo(bank.getId());
    }

    @Test
    void listIncludesInactiveAndCurrentBalanceUsesPostedTransactionsOpeningDateAndTransfers() {
        TestContext ctx = createContext("wallet_balance", WorkspaceRole.OWNER);
        LocalDate openingDate = LocalDate.of(2026, 6, 1);
        WalletResponse primaryRes = walletService.create(ctx.workspace().getId(), walletRequest("Primary", WalletType.CASH, bd("100"), openingDate, false, true), ctx.user().getId());
        WalletResponse secondaryRes = walletService.create(ctx.workspace().getId(), walletRequest("Secondary", WalletType.BANK, BigDecimal.ZERO, null, false, true), ctx.user().getId());
        Wallet primary = walletRepository.findById(primaryRes.getId()).orElseThrow();
        Wallet secondary = walletRepository.findById(secondaryRes.getId()).orElseThrow();

        transaction(ctx, primary, TransactionType.INCOME, TransactionStatus.POSTED, "1000", openingDate.minusDays(1), false, false);
        transaction(ctx, primary, TransactionType.INCOME, TransactionStatus.POSTED, "500", openingDate, false, false);
        transaction(ctx, primary, TransactionType.EXPENSE, TransactionStatus.POSTED, "100", openingDate, false, false);
        transaction(ctx, primary, TransactionType.LOAN_COLLECTION, TransactionStatus.POSTED, "30", openingDate, false, false);
        transaction(ctx, primary, TransactionType.LOAN_DISBURSEMENT, TransactionStatus.POSTED, "10", openingDate, false, false);
        transaction(ctx, primary, TransactionType.BORROWING_RECEIPT, TransactionStatus.POSTED, "40", openingDate, false, false);
        transaction(ctx, primary, TransactionType.BORROWING_REPAYMENT, TransactionStatus.POSTED, "5", openingDate, false, false);
        transaction(ctx, primary, TransactionType.INCOME, TransactionStatus.PLANNED, "999", openingDate, false, false);
        transaction(ctx, primary, TransactionType.INCOME, TransactionStatus.DRAFT, "999", openingDate, false, false);
        transaction(ctx, primary, TransactionType.INCOME, TransactionStatus.VOID, "999", openingDate, false, false);
        transaction(ctx, primary, TransactionType.INCOME, TransactionStatus.POSTED, "999", openingDate, false, true);
        transaction(ctx, primary, TransactionType.INCOME, TransactionStatus.POSTED, "999", openingDate, true, false);
        transfer(ctx, primary, secondary, "50", openingDate);
        transfer(ctx, secondary, primary, "20", openingDate);

        assertThat(walletService.calculateCurrentBalance(primary.getId())).isEqualByComparingTo("525");

        walletService.toggleStatus(ctx.workspace().getId(), secondary.getId(), false, ctx.user().getId());
        List<WalletResponse> wallets = walletService.list(ctx.workspace().getId(), ctx.user().getId());
        assertThat(wallets).extracting(WalletResponse::getId).containsExactly(primary.getId(), secondary.getId());
        assertThat(wallets).extracting(WalletResponse::getId).contains(primary.getId(), secondary.getId());
        assertThat(wallets.stream().filter(w -> w.getId().equals(secondary.getId())).findFirst().orElseThrow().isActive()).isFalse();

        WalletResponse fetched = walletService.get(ctx.workspace().getId(), primary.getId(), ctx.user().getId());
        assertThat(fetched.getCurrentBalance()).isEqualByComparingTo("525");

        WalletSummaryResponse summary = walletService.summary(ctx.workspace().getId(), ctx.user().getId());
        assertThat(summary.getTotalBalance()).isEqualByComparingTo("525");
        assertThat(summary.getActiveWalletCount()).isEqualTo(1);
        assertThat(summary.getDefaultWalletId()).isEqualTo(primary.getId());
    }

    @Test
    void authorizationAndWorkspaceScopingAreEnforced() {
        TestContext owner = createContext("wallet_auth_owner", WorkspaceRole.OWNER);
        TestContext viewer = createContext("wallet_auth_viewer", WorkspaceRole.VIEWER);
        TestContext outsider = createContext("wallet_auth_outsider", WorkspaceRole.OWNER);
        WalletResponse ownerWallet = walletService.create(owner.workspace().getId(), walletRequest("Owner Cash", WalletType.CASH, BigDecimal.ZERO, null, false, true), owner.user().getId());

        workspaceMemberRepository.save(WorkspaceMember.builder()
                .workspace(owner.workspace())
                .user(viewer.user())
                .role(WorkspaceRole.VIEWER)
                .build());

        assertThat(walletService.list(owner.workspace().getId(), viewer.user().getId())).hasSize(1);
        assertThatThrownBy(() -> walletService.create(owner.workspace().getId(), walletRequest("Viewer Wallet", WalletType.CASH, BigDecimal.ZERO, null, false, true), viewer.user().getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("FORBIDDEN");
        assertThatThrownBy(() -> walletService.list(owner.workspace().getId(), outsider.user().getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("WORKSPACE_ACCESS_DENIED");
        assertThatThrownBy(() -> walletService.update(outsider.workspace().getId(), ownerWallet.getId(), walletRequest("Stolen", WalletType.CASH, BigDecimal.ZERO, null, false, true), outsider.user().getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("WALLET_NOT_FOUND");
    }

    @Test
    void lastActiveWalletCannotBeDisabled() {
        TestContext ctx = createContext("last_active", WorkspaceRole.OWNER);
        WalletResponse only = walletService.create(ctx.workspace().getId(), walletRequest("Only", WalletType.CASH, BigDecimal.ZERO, null, false, true), ctx.user().getId());
        Wallet wallet = walletRepository.findById(only.getId()).orElseThrow();
        wallet.setDefault(false);
        walletRepository.saveAndFlush(wallet);

        assertThatThrownBy(() -> walletService.toggleStatus(ctx.workspace().getId(), only.getId(), false, ctx.user().getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("LAST_ACTIVE_WALLET_CANNOT_BE_DISABLED");
    }

    private TestContext createContext(String prefix, WorkspaceRole role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .fullName("Wallet Test User")
                .build());
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .name(prefix + " workspace")
                .createdByUser(user)
                .build());
        workspaceMemberRepository.save(WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(role)
                .build());
        return new TestContext(user, workspace);
    }

    private WalletRequest walletRequest(String name, WalletType type, BigDecimal openingBalance, LocalDate openingDate, boolean isDefault, boolean includeInTotal) {
        WalletRequest req = new WalletRequest();
        req.setName(name);
        req.setWalletType(type.name());
        req.setOpeningBalance(openingBalance);
        req.setOpeningDate(openingDate);
        req.setIsDefault(isDefault);
        req.setIncludeInTotal(includeInTotal);
        return req;
    }

    private Transaction transaction(TestContext ctx, Wallet wallet, TransactionType type, TransactionStatus status, String amount, LocalDate date, boolean deleted, boolean walletUnknown) {
        Transaction tx = Transaction.builder()
                .workspace(ctx.workspace())
                .createdByUser(ctx.user())
                .wallet(wallet)
                .transactionType(type)
                .transactionStatus(status)
                .amount(bd(amount))
                .transactionDate(date)
                .walletUnknown(walletUnknown)
                .historical(walletUnknown)
                .affectsWalletBalance(!walletUnknown)
                .build();
        if (deleted) {
            tx.setDeletedAt(Instant.now());
        }
        return transactionRepository.saveAndFlush(tx);
    }

    private Transaction transfer(TestContext ctx, Wallet source, Wallet destination, String amount, LocalDate date) {
        Transaction tx = transaction(ctx, source, TransactionType.TRANSFER, TransactionStatus.POSTED, amount, date, false, false);
        entityManager.createNativeQuery("""
                INSERT INTO transfer_details (transaction_id, source_wallet_id, destination_wallet_id)
                VALUES (:transactionId, :sourceWalletId, :destinationWalletId)
                """)
                .setParameter("transactionId", tx.getId())
                .setParameter("sourceWalletId", source.getId())
                .setParameter("destinationWalletId", destination.getId())
                .executeUpdate();
        entityManager.flush();
        return tx;
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private record TestContext(User user, Workspace workspace) {
    }
}
