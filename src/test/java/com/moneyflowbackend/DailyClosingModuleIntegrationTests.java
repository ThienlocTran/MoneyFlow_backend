package com.moneyflowbackend;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.closing.dto.CompleteDailyClosingRequest;
import com.moneyflowbackend.closing.dto.DailyClosingResponse;
import com.moneyflowbackend.closing.dto.ReconciliationAdjustmentRequest;
import com.moneyflowbackend.closing.dto.WalletSnapshotPageResponse;
import com.moneyflowbackend.closing.dto.WalletSnapshotRequest;
import com.moneyflowbackend.closing.model.DailyClosing;
import com.moneyflowbackend.closing.model.DailyClosingStatus;
import com.moneyflowbackend.closing.repository.DailyClosingRepository;
import com.moneyflowbackend.closing.service.DailyClosingService;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.transaction.audit.TransactionAuditLogRepository;
import com.moneyflowbackend.transaction.model.AdjustmentDirection;
import com.moneyflowbackend.transaction.model.Transaction;
import com.moneyflowbackend.transaction.model.TransactionSourceType;
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
import com.moneyflowbackend.wallet.service.WalletBalanceService;
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
    @Autowired TransactionAuditLogRepository transactionAuditLogRepository;
    @Autowired WalletBalanceService walletBalanceService;

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

    @Test
    void reconcileSnapshotCreatesIncreaseAdjustmentLinksAuditAndPreservesEvidence() {
        TestContext owner = context("reco_increase", WorkspaceRole.OWNER);
        LocalDate closingDate = LocalDate.of(2026, 7, 20);
        Wallet cash = wallet(owner, "Cash", "100", closingDate.minusDays(1), true);
        DailyClosingResponse saved = dailyClosingService.saveWalletSnapshot(
                owner.workspace().getId(), closingDate, cash.getId(), snapshotReq("160", "MANUAL"), owner.user().getId());
        WalletBalanceSnapshot before = snapshotRepository.findByDailyClosingIdAndWalletId(saved.getClosingId(), cash.getId()).orElseThrow();

        DailyClosingResponse response = dailyClosingService.reconcileWalletSnapshot(
                owner.workspace().getId(),
                before.getId(),
                adjustmentReq(AdjustmentDirection.INCREASE, "60", true, "cash counted higher"),
                owner.user().getId());

        WalletBalanceSnapshot snapshot = snapshotRepository.findById(before.getId()).orElseThrow();
        Transaction tx = snapshot.getAdjustmentTransaction();
        assertThat(response.getStatus()).isEqualTo("OPEN");
        assertThat(snapshot.getReconciliationStatus()).isEqualTo(ReconciliationStatus.ADJUSTED);
        assertThat(snapshot.getBalance()).isEqualByComparingTo("160");
        assertThat(snapshot.getLedgerBalance()).isEqualByComparingTo("100");
        assertThat(snapshot.getDifference()).isEqualByComparingTo("60");
        assertThat(tx.getTransactionType()).isEqualTo(TransactionType.ADJUSTMENT);
        assertThat(tx.getAdjustmentDirection()).isEqualTo(AdjustmentDirection.INCREASE);
        assertThat(tx.getAmount()).isEqualByComparingTo("60");
        assertThat(tx.getTransactionStatus()).isEqualTo(TransactionStatus.POSTED);
        assertThat(tx.isAffectsWalletBalance()).isTrue();
        assertThat(tx.isHistorical()).isFalse();
        assertThat(tx.getTransactionDate()).isEqualTo(closingDate);
        assertThat(tx.getWallet().getId()).isEqualTo(cash.getId());
        assertThat(tx.getWorkspace().getId()).isEqualTo(owner.workspace().getId());
        assertThat(tx.getCreatedByUser().getId()).isEqualTo(owner.user().getId());
        assertThat(tx.getCategory()).isNull();
        assertThat(tx.getSourceType()).isEqualTo(TransactionSourceType.MANUAL);
        assertThat(walletBalanceService.calculateCurrentBalance(cash.getId())).isEqualByComparingTo("160");
        assertThat(walletBalanceService.calculateBalanceAtEndOfDay(cash.getId(), closingDate.minusDays(1))).isEqualByComparingTo("100");
        assertThat(transactionAuditLogRepository.findByWorkspaceIdAndTransactionIdOrderByCreatedAtAsc(owner.workspace().getId(), tx.getId()))
                .singleElement()
                .satisfies(log -> {
                    assertThat(log.getAction().name()).isEqualTo("CREATE");
                    assertThat(log.getActorUser().getId()).isEqualTo(owner.user().getId());
                    assertThat(log.getAfterData()).containsEntry("type", "ADJUSTMENT");
                    assertThat(log.getAfterData()).containsEntry("adjustmentDirection", "INCREASE");
                    assertThat(new BigDecimal(log.getAfterData().get("amount").toString())).isEqualByComparingTo("60");
                });
    }

    @Test
    void editorCanReconcileDecreaseAfterCompletedClosingWithoutChangingClosingState() {
        TestContext owner = context("reco_decrease", WorkspaceRole.OWNER);
        TestContext editor = context("reco_decrease_editor", WorkspaceRole.EDITOR);
        workspaceMemberRepository.save(WorkspaceMember.builder().workspace(owner.workspace()).user(editor.user()).role(WorkspaceRole.EDITOR).build());
        LocalDate closingDate = LocalDate.of(2026, 7, 20);
        Wallet cash = wallet(owner, "Cash", "100", closingDate.minusDays(1), true);
        DailyClosingResponse saved = dailyClosingService.saveWalletSnapshot(
                owner.workspace().getId(), closingDate, cash.getId(), snapshotReq("40", "MANUAL"), owner.user().getId());
        CompleteDailyClosingRequest completeReq = new CompleteDailyClosingRequest();
        DailyClosingResponse completed = dailyClosingService.completeDailyClosing(owner.workspace().getId(), closingDate, completeReq, owner.user().getId());
        Instant completedAt = completed.getCompletedAt();
        UUID snapshotId = snapshotRepository.findByDailyClosingIdAndWalletId(saved.getClosingId(), cash.getId()).orElseThrow().getId();

        dailyClosingService.reconcileWalletSnapshot(
                owner.workspace().getId(),
                snapshotId,
                adjustmentReq(AdjustmentDirection.DECREASE, "60", true, "cash short"),
                editor.user().getId());

        DailyClosing closing = dailyClosingRepository.findById(saved.getClosingId()).orElseThrow();
        WalletBalanceSnapshot snapshot = snapshotRepository.findById(snapshotId).orElseThrow();
        Transaction tx = snapshot.getAdjustmentTransaction();
        assertThat(closing.getStatus()).isEqualTo(DailyClosingStatus.COMPLETED);
        assertThat(closing.getCompletedAt()).isEqualTo(completedAt);
        assertThat(tx.getAdjustmentDirection()).isEqualTo(AdjustmentDirection.DECREASE);
        assertThat(tx.getAmount()).isEqualByComparingTo("60");
        assertThat(tx.getCreatedByUser().getId()).isEqualTo(editor.user().getId());
        assertThat(walletBalanceService.calculateBalanceAtEndOfDay(cash.getId(), closingDate)).isEqualByComparingTo("40");
    }

    @Test
    void reconcileRejectsMismatchedRequestsAndDuplicateAdjustmentWithoutCreatingRows() {
        TestContext owner = context("reco_validation", WorkspaceRole.OWNER);
        TestContext viewer = context("reco_validation_viewer", WorkspaceRole.VIEWER);
        workspaceMemberRepository.save(WorkspaceMember.builder().workspace(owner.workspace()).user(viewer.user()).role(WorkspaceRole.VIEWER).build());
        LocalDate closingDate = LocalDate.of(2026, 7, 20);
        Wallet cash = wallet(owner, "Cash", "100", closingDate.minusDays(1), true);
        DailyClosingResponse saved = dailyClosingService.saveWalletSnapshot(
                owner.workspace().getId(), closingDate, cash.getId(), snapshotReq("40", "MANUAL"), owner.user().getId());
        UUID snapshotId = snapshotRepository.findByDailyClosingIdAndWalletId(saved.getClosingId(), cash.getId()).orElseThrow().getId();
        long txCount = transactionRepository.count();

        assertBusinessCode(() -> dailyClosingService.reconcileWalletSnapshot(owner.workspace().getId(), snapshotId, adjustmentReq(AdjustmentDirection.DECREASE, "60", null, null), owner.user().getId()), "CONFIRMATION_REQUIRED");
        assertBusinessCode(() -> dailyClosingService.reconcileWalletSnapshot(owner.workspace().getId(), snapshotId, adjustmentReq(AdjustmentDirection.DECREASE, "60", false, null), owner.user().getId()), "CONFIRMATION_REQUIRED");
        assertBusinessCode(() -> dailyClosingService.reconcileWalletSnapshot(owner.workspace().getId(), snapshotId, adjustmentReq(null, "60", true, null), owner.user().getId()), "INVALID_ADJUSTMENT_DIRECTION");
        assertBusinessCode(() -> dailyClosingService.reconcileWalletSnapshot(owner.workspace().getId(), snapshotId, adjustmentReq(AdjustmentDirection.INCREASE, "60", true, null), owner.user().getId()), "INVALID_ADJUSTMENT_DIRECTION");
        assertBusinessCode(() -> dailyClosingService.reconcileWalletSnapshot(owner.workspace().getId(), snapshotId, adjustmentReq(AdjustmentDirection.DECREASE, null, true, null), owner.user().getId()), "INVALID_ADJUSTMENT_AMOUNT");
        assertBusinessCode(() -> dailyClosingService.reconcileWalletSnapshot(owner.workspace().getId(), snapshotId, adjustmentReq(AdjustmentDirection.DECREASE, "0", true, null), owner.user().getId()), "INVALID_ADJUSTMENT_AMOUNT");
        assertBusinessCode(() -> dailyClosingService.reconcileWalletSnapshot(owner.workspace().getId(), snapshotId, adjustmentReq(AdjustmentDirection.DECREASE, "-1", true, null), owner.user().getId()), "INVALID_ADJUSTMENT_AMOUNT");
        assertBusinessCode(() -> dailyClosingService.reconcileWalletSnapshot(owner.workspace().getId(), snapshotId, adjustmentReq(AdjustmentDirection.DECREASE, "30", true, null), owner.user().getId()), "ADJUSTMENT_MISMATCH");
        assertBusinessCode(() -> dailyClosingService.reconcileWalletSnapshot(owner.workspace().getId(), snapshotId, adjustmentReq(AdjustmentDirection.DECREASE, "70", true, null), owner.user().getId()), "ADJUSTMENT_MISMATCH");
        assertBusinessCode(() -> dailyClosingService.reconcileWalletSnapshot(owner.workspace().getId(), snapshotId, adjustmentReq(AdjustmentDirection.DECREASE, "60", true, null), viewer.user().getId()), "FORBIDDEN");
        assertThat(transactionRepository.count()).isEqualTo(txCount);

        dailyClosingService.reconcileWalletSnapshot(owner.workspace().getId(), snapshotId, adjustmentReq(AdjustmentDirection.DECREASE, "60", true, null), owner.user().getId());
        assertBusinessCode(() -> dailyClosingService.reconcileWalletSnapshot(owner.workspace().getId(), snapshotId, adjustmentReq(AdjustmentDirection.DECREASE, "60", true, null), owner.user().getId()), "SNAPSHOT_ALREADY_ADJUSTED");
        assertThat(transactionRepository.count()).isEqualTo(txCount + 1);
    }

    @Test
    void reconcileRejectsInvalidSnapshotStatesAndCrossWorkspaceLookup() {
        TestContext owner = context("reco_state", WorkspaceRole.OWNER);
        TestContext other = context("reco_state_other", WorkspaceRole.OWNER);
        LocalDate closingDate = LocalDate.of(2026, 7, 20);
        Wallet cash = wallet(owner, "Cash", "100", closingDate.minusDays(1), true);
        Wallet otherWallet = wallet(other, "Other", "100", closingDate.minusDays(1), true);
        DailyClosingResponse matched = dailyClosingService.saveWalletSnapshot(
                owner.workspace().getId(), closingDate, cash.getId(), snapshotReq("100", "MANUAL"), owner.user().getId());
        WalletBalanceSnapshot matchedSnapshot = snapshotRepository.findByDailyClosingIdAndWalletId(matched.getClosingId(), cash.getId()).orElseThrow();
        WalletBalanceSnapshot historical = snapshot(owner, cash, closingDate, "90", BalanceSourceType.EXCEL_MIGRATION, null, null, null);
        WalletBalanceSnapshot noClosing = snapshot(owner, cash, closingDate.plusDays(1), "90", BalanceSourceType.MANUAL, "100", "-10", ReconciliationStatus.UNRESOLVED);
        WalletBalanceSnapshot nullLedger = snapshot(owner, cash, closingDate.plusDays(2), "90", BalanceSourceType.MANUAL, null, "-10", ReconciliationStatus.UNRESOLVED);
        WalletBalanceSnapshot nullDifference = snapshot(owner, cash, closingDate.plusDays(3), "90", BalanceSourceType.MANUAL, "100", null, ReconciliationStatus.UNRESOLVED);
        WalletBalanceSnapshot otherSnapshot = snapshot(other, otherWallet, closingDate, "90", BalanceSourceType.MANUAL, "100", "-10", ReconciliationStatus.UNRESOLVED);

        assertBusinessCode(() -> dailyClosingService.reconcileWalletSnapshot(owner.workspace().getId(), UUID.randomUUID(), adjustmentReq(AdjustmentDirection.DECREASE, "10", true, null), owner.user().getId()), "SNAPSHOT_NOT_FOUND");
        assertBusinessCode(() -> dailyClosingService.reconcileWalletSnapshot(owner.workspace().getId(), otherSnapshot.getId(), adjustmentReq(AdjustmentDirection.DECREASE, "10", true, null), owner.user().getId()), "SNAPSHOT_NOT_FOUND");
        assertBusinessCode(() -> dailyClosingService.reconcileWalletSnapshot(owner.workspace().getId(), matchedSnapshot.getId(), adjustmentReq(AdjustmentDirection.DECREASE, "10", true, null), owner.user().getId()), "RECONCILIATION_NOT_NEEDED");
        assertBusinessCode(() -> dailyClosingService.reconcileWalletSnapshot(owner.workspace().getId(), historical.getId(), adjustmentReq(AdjustmentDirection.DECREASE, "10", true, null), owner.user().getId()), "HISTORICAL_SNAPSHOT_NOT_RECONCILABLE");
        assertBusinessCode(() -> dailyClosingService.reconcileWalletSnapshot(owner.workspace().getId(), noClosing.getId(), adjustmentReq(AdjustmentDirection.DECREASE, "10", true, null), owner.user().getId()), "SNAPSHOT_NOT_RECONCILABLE");
        assertBusinessCode(() -> dailyClosingService.reconcileWalletSnapshot(owner.workspace().getId(), nullLedger.getId(), adjustmentReq(AdjustmentDirection.DECREASE, "10", true, null), owner.user().getId()), "SNAPSHOT_NOT_RECONCILABLE");
        assertBusinessCode(() -> dailyClosingService.reconcileWalletSnapshot(owner.workspace().getId(), nullDifference.getId(), adjustmentReq(AdjustmentDirection.DECREASE, "10", true, null), owner.user().getId()), "SNAPSHOT_NOT_RECONCILABLE");
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

    private ReconciliationAdjustmentRequest adjustmentReq(AdjustmentDirection direction, String amount, Boolean confirmed, String note) {
        ReconciliationAdjustmentRequest req = new ReconciliationAdjustmentRequest();
        req.setDirection(direction);
        req.setAmount(amount == null ? null : new BigDecimal(amount));
        req.setConfirmed(confirmed);
        req.setNote(note);
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

    private record TestContext(User user, Workspace workspace) {
    }
}
