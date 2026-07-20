package com.moneyflowbackend;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.closing.dto.CompleteDailyClosingRequest;
import com.moneyflowbackend.closing.dto.DailyClosingResponse;
import com.moneyflowbackend.closing.dto.WalletSnapshotPageResponse;
import com.moneyflowbackend.closing.dto.WalletSnapshotRequest;
import com.moneyflowbackend.closing.model.DailyClosing;
import com.moneyflowbackend.closing.repository.DailyClosingRepository;
import com.moneyflowbackend.closing.service.DailyClosingService;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.transaction.model.Transaction;
import com.moneyflowbackend.transaction.model.TransactionStatus;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.wallet.model.BalanceSourceType;
import com.moneyflowbackend.wallet.model.ReconciliationStatus;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.model.WalletBalanceSnapshot;
import com.moneyflowbackend.wallet.model.WalletType;
import com.moneyflowbackend.wallet.repository.WalletBalanceSnapshotRepository;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DailyClosingModuleIntegrationTests {
    @Autowired DailyClosingService dailyClosingService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired WalletBalanceSnapshotRepository snapshotRepository;
    @Autowired DailyClosingRepository dailyClosingRepository;
    @Autowired TransactionRepository transactionRepository;

    @Test
    void getDailyClosingReturnsVirtualOpenSessionWithoutPersistingRows() {
        TestContext ctx = context("closing_get", WorkspaceRole.OWNER);
        LocalDate closingDate = LocalDate.of(2026, 7, 20);
        Wallet cash = wallet(ctx, "Cash", "100", closingDate.minusDays(1), true);
        wallet(ctx, "Future", "999", closingDate.plusDays(1), true);
        wallet(ctx, "Inactive", "50", closingDate.minusDays(1), false);
        tx(ctx, cash, "25", closingDate);

        DailyClosingResponse response = dailyClosingService.getDailyClosing(ctx.workspace().getId(), closingDate, ctx.user().getId());

        assertThat(response.getClosingId()).isNull();
        assertThat(response.getStatus()).isEqualTo("OPEN");
        assertThat(response.getWallets()).hasSize(1);
        assertThat(response.getWallets().getFirst().getWalletId()).isEqualTo(cash.getId());
        assertThat(response.getWallets().getFirst().getLedgerBalance()).isEqualByComparingTo("125");
        assertThat(response.getWallets().getFirst().getActualBalance()).isNull();
        assertThat(dailyClosingRepository.existsByWorkspaceIdAndClosingDate(ctx.workspace().getId(), closingDate)).isFalse();
        assertThat(snapshotRepository.count()).isZero();
    }

    @Test
    void savedSnapshotCreatesClosingCalculatesDifferenceAndExistingInactiveSnapshotStillLoads() {
        TestContext owner = context("closing_save_owner", WorkspaceRole.OWNER);
        TestContext editor = context("closing_save_editor", WorkspaceRole.EDITOR);
        TestContext viewer = context("closing_save_viewer", WorkspaceRole.VIEWER);
        workspaceMemberRepository.save(WorkspaceMember.builder().workspace(owner.workspace()).user(editor.user()).role(WorkspaceRole.EDITOR).build());
        workspaceMemberRepository.save(WorkspaceMember.builder().workspace(owner.workspace()).user(viewer.user()).role(WorkspaceRole.VIEWER).build());

        LocalDate closingDate = LocalDate.of(2026, 7, 20);
        Wallet cash = wallet(owner, "Cash", "100", closingDate.minusDays(1), true);
        Wallet bank = wallet(owner, "Bank", "0", closingDate.minusDays(1), true);
        tx(owner, cash, "25", closingDate);

        DailyClosingResponse saved = dailyClosingService.saveWalletSnapshot(
                owner.workspace().getId(), closingDate, cash.getId(), snapshotReq("125", "MANUAL"), owner.user().getId());
        assertThat(saved.getClosingId()).isNotNull();
        assertThat(snapshotRepository.findAllByDailyClosingId(saved.getClosingId())).hasSize(1);
        assertThat(saved.getWallets()).filteredOn(w -> w.getWalletId().equals(cash.getId()))
                .first()
                .satisfies(w -> {
                    assertThat(w.getDifference()).isEqualByComparingTo("0");
                    assertThat(w.getReconciliationStatus()).isEqualTo("MATCHED");
                });

        dailyClosingService.saveWalletSnapshot(
                owner.workspace().getId(), closingDate, cash.getId(), snapshotReq("0", null), editor.user().getId());
        assertThat(snapshotRepository.findAllByDailyClosingId(saved.getClosingId())).hasSize(1);
        assertThat(snapshotRepository.findByDailyClosingIdAndWalletId(saved.getClosingId(), cash.getId()).orElseThrow().getDifference())
                .isEqualByComparingTo("-125");

        dailyClosingService.saveWalletSnapshot(
                owner.workspace().getId(), closingDate, bank.getId(), snapshotReq("1", "MANUAL"), owner.user().getId());
        bank.setActive(false);
        walletRepository.saveAndFlush(bank);

        DailyClosingResponse reloaded = dailyClosingService.getDailyClosing(owner.workspace().getId(), closingDate, viewer.user().getId());
        assertThat(reloaded.getWallets()).extracting("walletId").contains(bank.getId());
        assertThatThrownBy(() -> dailyClosingService.saveWalletSnapshot(
                owner.workspace().getId(), closingDate, cash.getId(), snapshotReq("1", "MANUAL"), viewer.user().getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("FORBIDDEN");
    }

    @Test
    void saveSnapshotRejectsInvalidWalletStateSourceTypeAndAdjustedSnapshot() {
        TestContext owner = context("closing_rules", WorkspaceRole.OWNER);
        TestContext other = context("closing_rules_other", WorkspaceRole.OWNER);
        LocalDate closingDate = LocalDate.of(2026, 7, 20);
        Wallet cash = wallet(owner, "Cash", "0", closingDate, true);
        Wallet inactive = wallet(owner, "Inactive", "0", closingDate, false);
        Wallet future = wallet(owner, "Future", "0", closingDate.plusDays(1), true);
        Wallet otherWallet = wallet(other, "Other Cash", "0", closingDate, true);

        assertThatThrownBy(() -> dailyClosingService.saveWalletSnapshot(owner.workspace().getId(), closingDate, inactive.getId(), snapshotReq("1", "MANUAL"), owner.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("WALLET_INACTIVE");
        assertThatThrownBy(() -> dailyClosingService.saveWalletSnapshot(owner.workspace().getId(), closingDate, future.getId(), snapshotReq("1", "MANUAL"), owner.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("WALLET_NOT_OPEN_ON_DATE");
        assertThatThrownBy(() -> dailyClosingService.saveWalletSnapshot(owner.workspace().getId(), closingDate, otherWallet.getId(), snapshotReq("1", "MANUAL"), owner.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("WALLET_NOT_FOUND");
        assertThatThrownBy(() -> dailyClosingService.saveWalletSnapshot(owner.workspace().getId(), closingDate, cash.getId(), snapshotReq("-1", "MANUAL"), owner.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("INVALID_ACTUAL_BALANCE");
        assertThatThrownBy(() -> dailyClosingService.saveWalletSnapshot(owner.workspace().getId(), closingDate, cash.getId(), snapshotReq("1", "VOICE"), owner.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("VALIDATION_ERROR");
        assertThatThrownBy(() -> dailyClosingService.saveWalletSnapshot(owner.workspace().getId(), closingDate, cash.getId(), snapshotReq("1", "EXCEL_MIGRATION"), owner.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("VALIDATION_ERROR");

        DailyClosingResponse saved = dailyClosingService.saveWalletSnapshot(owner.workspace().getId(), closingDate, cash.getId(), snapshotReq("1", "MANUAL"), owner.user().getId());
        WalletBalanceSnapshot snapshot = snapshotRepository.findByDailyClosingIdAndWalletId(saved.getClosingId(), cash.getId()).orElseThrow();
        snapshot.setReconciliationStatus(ReconciliationStatus.ADJUSTED);
        snapshotRepository.saveAndFlush(snapshot);

        assertThatThrownBy(() -> dailyClosingService.saveWalletSnapshot(owner.workspace().getId(), closingDate, cash.getId(), snapshotReq("2", "MANUAL"), owner.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("SNAPSHOT_ALREADY_ADJUSTED");
    }

    @Test
    void completeClosingAllowsMissingAndUnresolvedSnapshotsAndIsIdempotent() {
        TestContext owner = context("closing_complete", WorkspaceRole.OWNER);
        TestContext viewer = context("closing_complete_viewer", WorkspaceRole.VIEWER);
        workspaceMemberRepository.save(WorkspaceMember.builder().workspace(owner.workspace()).user(viewer.user()).role(WorkspaceRole.VIEWER).build());
        LocalDate closingDate = LocalDate.of(2026, 7, 20);
        Wallet cash = wallet(owner, "Cash", "100", closingDate, true);
        wallet(owner, "Bank", "0", closingDate, true);
        long transactionCount = transactionRepository.count();

        dailyClosingService.saveWalletSnapshot(owner.workspace().getId(), closingDate, cash.getId(), snapshotReq("90", "MANUAL"), owner.user().getId());
        CompleteDailyClosingRequest request = new CompleteDailyClosingRequest();
        request.setNote("checked");
        DailyClosingResponse completed = dailyClosingService.completeDailyClosing(owner.workspace().getId(), closingDate, request, owner.user().getId());
        Instant completedAt = completed.getCompletedAt();

        assertThat(completed.getStatus()).isEqualTo("COMPLETED");
        assertThat(completed.getCompletedByUserId()).isEqualTo(owner.user().getId());
        assertThat(snapshotRepository.findAllByDailyClosingId(completed.getClosingId())).hasSize(1);
        assertThat(transactionRepository.count()).isEqualTo(transactionCount);

        DailyClosingResponse second = dailyClosingService.completeDailyClosing(owner.workspace().getId(), closingDate, request, owner.user().getId());
        assertThat(second.getCompletedAt()).isEqualTo(completedAt);
        assertThatThrownBy(() -> dailyClosingService.saveWalletSnapshot(owner.workspace().getId(), closingDate, cash.getId(), snapshotReq("91", "MANUAL"), owner.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("DAILY_CLOSING_COMPLETED");
        assertThatThrownBy(() -> dailyClosingService.completeDailyClosing(owner.workspace().getId(), closingDate.plusDays(1), request, viewer.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("FORBIDDEN");
    }

    @Test
    void snapshotHistoryFiltersWorkspaceWalletDateAndPreservesExcelNulls() {
        TestContext owner = context("closing_history", WorkspaceRole.OWNER);
        TestContext other = context("closing_history_other", WorkspaceRole.OWNER);
        LocalDate day1 = LocalDate.of(2026, 7, 1);
        LocalDate day2 = LocalDate.of(2026, 7, 2);
        Wallet cash = wallet(owner, "Cash", "0", day1, true);
        Wallet bank = wallet(owner, "Bank", "0", day1, true);
        Wallet otherWallet = wallet(other, "Other", "0", day1, true);

        snapshot(owner, cash, day1, "100", BalanceSourceType.EXCEL_MIGRATION, null, null, null);
        snapshot(owner, cash, day2, "110", BalanceSourceType.MANUAL, "100", "10", ReconciliationStatus.UNRESOLVED);
        snapshot(owner, bank, day2, "50", BalanceSourceType.MANUAL, "50", "0", ReconciliationStatus.MATCHED);
        snapshot(other, otherWallet, day2, "999", BalanceSourceType.MANUAL, "999", "0", ReconciliationStatus.MATCHED);

        WalletSnapshotPageResponse page = dailyClosingService.listSnapshotHistory(owner.workspace().getId(), cash.getId(), day1, day2, 0, 10, owner.user().getId());
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent()).extracting("walletId").containsOnly(cash.getId());
        assertThat(page.getContent()).extracting("actualBalance").doesNotContain(new BigDecimal("999"));
        assertThat(page.getContent()).filteredOn(i -> i.getSourceType().equals("EXCEL_MIGRATION"))
                .first()
                .satisfies(i -> {
                    assertThat(i.getDailyClosingId()).isNull();
                    assertThat(i.getLedgerBalance()).isNull();
                    assertThat(i.getDifference()).isNull();
                    assertThat(i.getReconciliationStatus()).isNull();
                });

        assertThatThrownBy(() -> dailyClosingService.listSnapshotHistory(owner.workspace().getId(), cash.getId(), day2, day1, 0, 10, owner.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("INVALID_DATE_RANGE");
        assertThatThrownBy(() -> dailyClosingService.listSnapshotHistory(owner.workspace().getId(), otherWallet.getId(), null, null, 0, 10, owner.user().getId()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("WALLET_NOT_FOUND");
    }

    private TestContext context(String prefix, WorkspaceRole role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .fullName("Daily Closing Test")
                .build());
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .name(prefix + " workspace")
                .createdByUser(user)
                .build());
        workspaceMemberRepository.save(WorkspaceMember.builder().workspace(workspace).user(user).role(role).build());
        return new TestContext(user, workspace);
    }

    private Wallet wallet(TestContext ctx, String name, String openingBalance, LocalDate openingDate, boolean active) {
        return walletRepository.saveAndFlush(Wallet.builder()
                .workspace(ctx.workspace())
                .name(name)
                .walletType(WalletType.CASH)
                .openingBalance(new BigDecimal(openingBalance))
                .openingDate(openingDate)
                .isActive(active)
                .includeInTotal(true)
                .build());
    }

    private WalletSnapshotRequest snapshotReq(String actualBalance, String sourceType) {
        WalletSnapshotRequest req = new WalletSnapshotRequest();
        req.setActualBalance(new BigDecimal(actualBalance));
        req.setRecordedAt(Instant.parse("2026-07-20T15:15:00Z"));
        req.setSourceType(sourceType);
        return req;
    }

    private Transaction tx(TestContext ctx, Wallet wallet, String amount, LocalDate date) {
        return transactionRepository.saveAndFlush(Transaction.builder()
                .workspace(ctx.workspace())
                .createdByUser(ctx.user())
                .wallet(wallet)
                .transactionType(TransactionType.INCOME)
                .transactionStatus(TransactionStatus.POSTED)
                .amount(new BigDecimal(amount))
                .transactionDate(date)
                .walletUnknown(false)
                .affectsWalletBalance(true)
                .build());
    }

    private WalletBalanceSnapshot snapshot(
            TestContext ctx,
            Wallet wallet,
            LocalDate date,
            String balance,
            BalanceSourceType sourceType,
            String ledgerBalance,
            String difference,
            ReconciliationStatus status) {
        return snapshotRepository.saveAndFlush(WalletBalanceSnapshot.builder()
                .workspace(ctx.workspace())
                .wallet(wallet)
                .snapshotDate(date)
                .balance(new BigDecimal(balance))
                .sourceType(sourceType)
                .ledgerBalance(ledgerBalance == null ? null : new BigDecimal(ledgerBalance))
                .difference(difference == null ? null : new BigDecimal(difference))
                .reconciliationStatus(status)
                .createdAt(Instant.now())
                .build());
    }

    private record TestContext(User user, Workspace workspace) {
    }
}
