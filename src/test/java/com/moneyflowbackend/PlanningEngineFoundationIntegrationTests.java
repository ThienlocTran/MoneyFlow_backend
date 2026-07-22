package com.moneyflowbackend;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.planning.dto.ActuallySpendableResponse;
import com.moneyflowbackend.planning.model.PlanningHorizon;
import com.moneyflowbackend.planning.service.PlanningService;
import com.moneyflowbackend.transaction.model.AdjustmentDirection;
import com.moneyflowbackend.transaction.model.Transaction;
import com.moneyflowbackend.transaction.model.TransactionStatus;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.transaction.model.TransferDetail;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.transaction.repository.TransferDetailRepository;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlanningEngineFoundationIntegrationTests {
    @Autowired PlanningService planningService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired TransferDetailRepository transferDetailRepository;

    @Test
    void defaultWalletSelectionUsesActiveIncludedWalletsAndCurrentMonthRange() {
        TestContext ctx = createContext("planning_default", WorkspaceRole.OWNER);
        Wallet cash = wallet(ctx, "Cash", "1000", true, true);
        Wallet hidden = wallet(ctx, "Hidden", "999", true, false);
        wallet(ctx, "Inactive", "999", false, true);

        ActuallySpendableResponse response = planningService.actuallySpendable(ctx.workspace().getId(), ctx.user().getId(), null, null, null, null);

        assertThat(response.horizon()).isEqualTo(PlanningHorizon.CURRENT_MONTH);
        assertThat(response.from()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(response.to()).isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(response.selectedWallets()).extracting("id").containsExactly(cash.getId());
        assertThat(response.selectedWallets()).extracting("id").doesNotContain(hidden.getId());
        assertThat(response.availableLedger()).isEqualByComparingTo("1000");
        assertThat(response.actuallySpendable()).isEqualByComparingTo("1000");
        assertThat(response.incomplete()).isFalse();
    }

    @Test
    void customRangeValidationAndExplicitWalletIsolationFailSafely() {
        TestContext ctx = createContext("planning_custom", WorkspaceRole.OWNER);
        TestContext other = createContext("planning_other", WorkspaceRole.OWNER);
        Wallet wallet = wallet(ctx, "Cash", "100", true, true);
        Wallet otherWallet = wallet(other, "Other", "100", true, true);

        ActuallySpendableResponse response = planningService.actuallySpendable(
                ctx.workspace().getId(),
                ctx.user().getId(),
                PlanningHorizon.CUSTOM,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                List.of(wallet.getId()));

        assertThat(response.from()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(response.selectedWallets()).extracting("id").containsExactly(wallet.getId());
        assertThatThrownBy(() -> planningService.actuallySpendable(ctx.workspace().getId(), ctx.user().getId(), PlanningHorizon.CUSTOM, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("requires from and to");
        assertThatThrownBy(() -> planningService.actuallySpendable(ctx.workspace().getId(), ctx.user().getId(), PlanningHorizon.CUSTOM, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 1), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("to must be on or after from");
        assertThatThrownBy(() -> planningService.actuallySpendable(ctx.workspace().getId(), ctx.user().getId(), PlanningHorizon.CUSTOM, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), List.of(otherWallet.getId())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("missing or inaccessible");
    }

    @Test
    void ledgerBalanceNetsSelectedTransfersAndIncludesAdjustments() {
        TestContext ctx = createContext("planning_ledger", WorkspaceRole.OWNER);
        Wallet cash = wallet(ctx, "Cash", "1000", true, true);
        Wallet bank = wallet(ctx, "Bank", "200", true, true);
        transfer(ctx, cash, bank, "100");
        adjustment(ctx, cash, "50", AdjustmentDirection.INCREASE);

        ActuallySpendableResponse both = planningService.actuallySpendable(ctx.workspace().getId(), ctx.user().getId(), null, null, null, List.of(cash.getId(), bank.getId()));
        ActuallySpendableResponse cashOnly = planningService.actuallySpendable(ctx.workspace().getId(), ctx.user().getId(), null, null, null, List.of(cash.getId()));

        assertThat(both.availableLedger()).isEqualByComparingTo("1250");
        assertThat(cashOnly.availableLedger()).isEqualByComparingTo("950");
    }

    @Test
    void viewerCanReadAndOutsiderCannotRead() {
        TestContext owner = createContext("planning_auth_owner", WorkspaceRole.OWNER);
        TestContext viewer = createContext("planning_auth_viewer", WorkspaceRole.OWNER);
        TestContext outsider = createContext("planning_auth_outsider", WorkspaceRole.OWNER);
        wallet(owner, "Cash", "100", true, true);
        workspaceMemberRepository.saveAndFlush(WorkspaceMember.builder().workspace(owner.workspace()).user(viewer.user()).role(WorkspaceRole.VIEWER).build());

        assertThat(planningService.actuallySpendable(owner.workspace().getId(), viewer.user().getId(), null, null, null, null).availableLedger())
                .isEqualByComparingTo("100");
        assertThatThrownBy(() -> planningService.actuallySpendable(owner.workspace().getId(), outsider.user().getId(), null, null, null, null))
                .isInstanceOf(BusinessException.class);
    }

    private TestContext createContext(String prefix, WorkspaceRole role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .fullName("Planning Test User")
                .build());
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .name(prefix + " workspace")
                .createdByUser(user)
                .build());
        workspaceMemberRepository.save(WorkspaceMember.builder().workspace(workspace).user(user).role(role).build());
        return new TestContext(user, workspace);
    }

    private Wallet wallet(TestContext ctx, String name, String openingBalance, boolean active, boolean includeInTotal) {
        return walletRepository.saveAndFlush(Wallet.builder()
                .workspace(ctx.workspace())
                .name(name)
                .walletType(WalletType.CASH)
                .openingBalance(new BigDecimal(openingBalance))
                .openingDate(LocalDate.of(2026, 7, 1))
                .isActive(active)
                .includeInTotal(includeInTotal)
                .build());
    }

    private void transfer(TestContext ctx, Wallet source, Wallet destination, String amount) {
        Transaction tx = tx(ctx, source, TransactionType.TRANSFER, amount);
        transferDetailRepository.saveAndFlush(TransferDetail.builder()
                .transactionId(tx.getId())
                .transaction(tx)
                .sourceWallet(source)
                .destinationWallet(destination)
                .build());
    }

    private void adjustment(TestContext ctx, Wallet wallet, String amount, AdjustmentDirection direction) {
        Transaction tx = tx(ctx, wallet, TransactionType.ADJUSTMENT, amount);
        tx.setAdjustmentDirection(direction);
        transactionRepository.saveAndFlush(tx);
    }

    private Transaction tx(TestContext ctx, Wallet wallet, TransactionType type, String amount) {
        return transactionRepository.saveAndFlush(Transaction.builder()
                .workspace(ctx.workspace())
                .createdByUser(ctx.user())
                .wallet(wallet)
                .transactionType(type)
                .transactionStatus(TransactionStatus.POSTED)
                .amount(new BigDecimal(amount))
                .transactionDate(LocalDate.of(2026, 7, 10))
                .walletUnknown(false)
                .affectsWalletBalance(true)
                .build());
    }

    private record TestContext(User user, Workspace workspace) {
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-07-21T17:00:00Z"), ZoneOffset.UTC);
        }
    }
}
