package com.moneyflowbackend.activity;

import com.moneyflowbackend.activity.domain.ActivityAction;
import com.moneyflowbackend.activity.domain.ActivityActorType;
import com.moneyflowbackend.activity.domain.ActivityEntityType;
import com.moneyflowbackend.activity.domain.ActivitySource;
import com.moneyflowbackend.activity.internal.ActivityCandidate;
import com.moneyflowbackend.activity.query.ActivityCursor;
import com.moneyflowbackend.activity.query.ActivityTimelineQuery;
import com.moneyflowbackend.activity.source.TransactionActivitySourceReader;
import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.closing.model.DailyClosing;
import com.moneyflowbackend.closing.repository.DailyClosingRepository;
import com.moneyflowbackend.obligation.model.ObligationAmountMode;
import com.moneyflowbackend.obligation.model.ObligationDirection;
import com.moneyflowbackend.obligation.model.ObligationFrequency;
import com.moneyflowbackend.obligation.model.ObligationOccurrence;
import com.moneyflowbackend.obligation.model.ObligationOccurrenceStatus;
import com.moneyflowbackend.obligation.model.RecurringObligationTemplate;
import com.moneyflowbackend.obligation.repository.ObligationOccurrenceRepository;
import com.moneyflowbackend.obligation.repository.RecurringObligationTemplateRepository;
import com.moneyflowbackend.transaction.audit.TransactionAuditAction;
import com.moneyflowbackend.transaction.audit.TransactionAuditLog;
import com.moneyflowbackend.transaction.audit.TransactionAuditLogRepository;
import com.moneyflowbackend.transaction.model.AdjustmentDirection;
import com.moneyflowbackend.transaction.model.Transaction;
import com.moneyflowbackend.transaction.model.TransactionSourceType;
import com.moneyflowbackend.transaction.model.TransactionStatus;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.transaction.model.TransferDetail;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.transaction.repository.TransferDetailRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TransactionActivitySourceReaderIntegrationTests {
    @Autowired TransactionActivitySourceReader reader;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired TransferDetailRepository transferDetailRepository;
    @Autowired TransactionAuditLogRepository auditLogRepository;
    @Autowired RecurringObligationTemplateRepository templateRepository;
    @Autowired ObligationOccurrenceRepository occurrenceRepository;
    @Autowired DailyClosingRepository dailyClosingRepository;
    @Autowired WalletBalanceSnapshotRepository snapshotRepository;

    @Test
    void mapsTransactionAuditActionsAndRedactsUnsafeDetails() {
        TestContext ctx = context("activity_tx");
        Transaction tx = transaction(ctx, TransactionType.EXPENSE, "100.00");
        Instant base = Instant.parse("2026-07-20T10:15:30Z");
        audit(ctx, tx, TransactionAuditAction.CREATE, base.minusSeconds(3));
        audit(ctx, tx, TransactionAuditAction.UPDATE, base.minusSeconds(2));
        audit(ctx, tx, TransactionAuditAction.DELETE, base.minusSeconds(1));
        audit(ctx, tx, TransactionAuditAction.RESTORE, base);
        audit(ctx, tx, TransactionAuditAction.IMPORT, base.plusSeconds(1));

        List<ActivityCandidate> candidates = reader.read(query(ctx.workspace().getId()), 10);

        assertThat(candidates).extracting(ActivityCandidate::action).containsExactly(
                ActivityAction.TRANSACTION_RESTORED,
                ActivityAction.TRANSACTION_VOIDED,
                ActivityAction.TRANSACTION_UPDATED,
                ActivityAction.TRANSACTION_CREATED);
        assertThat(candidates).allSatisfy(candidate -> {
            assertThat(candidate.entityType()).isEqualTo(ActivityEntityType.TRANSACTION);
            assertThat(candidate.entityId()).isEqualTo(tx.getId());
            assertThat(candidate.actor().type()).isEqualTo(ActivityActorType.USER);
            assertThat(candidate.actor().id()).isEqualTo(ctx.user().getId());
            assertThat(candidate.details()).containsKeys("transactionType", "transactionStatus", "walletId", "categoryId");
            assertThat(candidate.details()).doesNotContainKeys("note", "rawInput", "beforeState", "afterState", "token");
        });
    }

    @Test
    void createAuditUsesObligationTransferAdjustmentPrecedence() {
        TestContext ctx = context("activity_precedence");
        Instant base = Instant.parse("2026-07-20T10:15:30Z");
        Transaction normal = transaction(ctx, TransactionType.INCOME, "10.00");
        Transaction transfer = transfer(ctx, "20.00");
        Transaction adjustment = adjustment(ctx, "30.00");
        DailyClosing closing = dailyClosing(ctx, LocalDate.of(2026, 7, 20));
        snapshot(ctx, adjustment, closing);
        Transaction obligationTx = transaction(ctx, TransactionType.EXPENSE, "40.00");
        ObligationOccurrence occurrence = confirmedOccurrence(ctx, obligationTx, "40.00", ObligationDirection.PAYABLE);
        audit(ctx, normal, TransactionAuditAction.CREATE, base.minusSeconds(3));
        audit(ctx, transfer, TransactionAuditAction.CREATE, base.minusSeconds(2));
        audit(ctx, adjustment, TransactionAuditAction.CREATE, base.minusSeconds(1));
        audit(ctx, obligationTx, TransactionAuditAction.CREATE, base);

        List<ActivityCandidate> candidates = reader.read(query(ctx.workspace().getId()), 10);

        assertThat(candidates).extracting(ActivityCandidate::action).containsExactly(
                ActivityAction.OBLIGATION_CONFIRMED,
                ActivityAction.ADJUSTMENT_CREATED,
                ActivityAction.TRANSFER_CREATED,
                ActivityAction.TRANSACTION_CREATED);
        ActivityCandidate obligation = candidates.get(0);
        assertThat(obligation.entityType()).isEqualTo(ActivityEntityType.OBLIGATION_OCCURRENCE);
        assertThat(obligation.entityId()).isEqualTo(occurrence.getId());
        assertThat(obligation.direction()).isEqualTo("PAYABLE");
        assertThat(obligation.details()).containsEntry("linkedTransactionId", obligationTx.getId());
        assertThat(obligation.details()).containsEntry("obligationOccurrenceId", occurrence.getId());

        ActivityCandidate adjustmentCandidate = candidates.get(1);
        assertThat(adjustmentCandidate.entityType()).isEqualTo(ActivityEntityType.TRANSACTION);
        assertThat(adjustmentCandidate.details()).containsEntry("adjustmentDirection", "INCREASE");
        assertThat(adjustmentCandidate.details()).containsEntry("dailyClosingId", closing.getId());

        ActivityCandidate transferCandidate = candidates.get(2);
        assertThat(transferCandidate.entityType()).isEqualTo(ActivityEntityType.TRANSFER);
        assertThat(transferCandidate.details()).containsKeys("sourceWalletId", "destinationWalletId");
    }

    @Test
    void filtersAndCursorDoNotRepeatSameTimestampRows() {
        TestContext ctx = context("activity_cursor");
        Transaction first = transaction(ctx, TransactionType.EXPENSE, "100.00");
        Transaction second = transaction(ctx, TransactionType.EXPENSE, "200.00");
        Instant same = Instant.parse("2026-07-20T10:15:30Z");
        audit(ctx, first, TransactionAuditAction.CREATE, same);
        audit(ctx, second, TransactionAuditAction.CREATE, same);

        List<ActivityCandidate> firstPage = reader.read(query(ctx.workspace().getId()), 1);
        ActivityCandidate cursorItem = firstPage.get(0);
        ActivityTimelineQuery pageTwoQuery = new ActivityTimelineQuery(
                ctx.workspace().getId(),
                Set.of(ActivityAction.TRANSACTION_CREATED),
                Set.of(ActivityEntityType.TRANSACTION),
                null,
                null,
                null,
                new ActivityCursor(cursorItem.occurredAt(), ActivitySource.TRANSACTION_AUDIT, cursorItem.activityId()),
                1);

        List<ActivityCandidate> secondPage = reader.read(pageTwoQuery, 1);

        assertThat(firstPage).hasSize(1);
        assertThat(secondPage).hasSize(1);
        assertThat(secondPage.get(0).activityId()).isNotEqualTo(cursorItem.activityId());
    }

    @Test
    void skipsBrokenCrossWorkspaceObligationLink() {
        TestContext owner = context("activity_owner");
        TestContext other = context("activity_other");
        Transaction tx = transaction(owner, TransactionType.EXPENSE, "40.00");
        confirmedOccurrence(other, tx, "40.00", ObligationDirection.RECEIVABLE);
        audit(owner, tx, TransactionAuditAction.CREATE, Instant.parse("2026-07-20T10:15:30Z"));

        List<ActivityCandidate> candidates = reader.read(query(owner.workspace().getId()), 10);

        assertThat(candidates).singleElement()
                .extracting(ActivityCandidate::action)
                .isEqualTo(ActivityAction.TRANSACTION_CREATED);
    }

    private ActivityTimelineQuery query(UUID workspaceId) {
        return new ActivityTimelineQuery(workspaceId, null, null, null, null, null, null, 30);
    }

    private TransactionAuditLog audit(TestContext ctx, Transaction tx, TransactionAuditAction action, Instant createdAt) {
        return auditLogRepository.saveAndFlush(TransactionAuditLog.builder()
                .workspace(ctx.workspace())
                .transaction(tx)
                .actorUser(ctx.user())
                .action(action)
                .beforeData(Map.of("note", "private before"))
                .afterData(Map.of("rawInput", "private after", "token", "secret"))
                .createdAt(createdAt)
                .build());
    }

    private Transaction transaction(TestContext ctx, TransactionType type, String amount) {
        return transactionRepository.saveAndFlush(Transaction.builder()
                .workspace(ctx.workspace())
                .createdByUser(ctx.user())
                .wallet(ctx.cash())
                .category(type == TransactionType.INCOME ? ctx.incomeCategory() : ctx.expenseCategory())
                .transactionType(type)
                .transactionStatus(TransactionStatus.POSTED)
                .amount(new BigDecimal(amount))
                .currency("VND")
                .transactionDate(LocalDate.of(2026, 7, 20))
                .note("private note")
                .rawInput("private raw input")
                .sourceType(TransactionSourceType.MANUAL)
                .build());
    }

    private Transaction transfer(TestContext ctx, String amount) {
        Transaction tx = transactionRepository.saveAndFlush(Transaction.builder()
                .workspace(ctx.workspace())
                .createdByUser(ctx.user())
                .wallet(ctx.cash())
                .transactionType(TransactionType.TRANSFER)
                .transactionStatus(TransactionStatus.POSTED)
                .amount(new BigDecimal(amount))
                .currency("VND")
                .transactionDate(LocalDate.of(2026, 7, 20))
                .sourceType(TransactionSourceType.MANUAL)
                .build());
        transferDetailRepository.saveAndFlush(TransferDetail.builder()
                .transactionId(tx.getId())
                .transaction(tx)
                .sourceWallet(ctx.cash())
                .destinationWallet(ctx.bank())
                .build());
        return tx;
    }

    private Transaction adjustment(TestContext ctx, String amount) {
        return transactionRepository.saveAndFlush(Transaction.builder()
                .workspace(ctx.workspace())
                .createdByUser(ctx.user())
                .wallet(ctx.cash())
                .transactionType(TransactionType.ADJUSTMENT)
                .adjustmentDirection(AdjustmentDirection.INCREASE)
                .transactionStatus(TransactionStatus.POSTED)
                .amount(new BigDecimal(amount))
                .currency("VND")
                .transactionDate(LocalDate.of(2026, 7, 20))
                .sourceReference("wallet_snapshot:pending")
                .sourceType(TransactionSourceType.MANUAL)
                .build());
    }

    private DailyClosing dailyClosing(TestContext ctx, LocalDate date) {
        return dailyClosingRepository.saveAndFlush(DailyClosing.builder()
                .workspace(ctx.workspace())
                .closingDate(date)
                .build());
    }

    private WalletBalanceSnapshot snapshot(TestContext ctx, Transaction adjustment, DailyClosing closing) {
        return snapshotRepository.saveAndFlush(WalletBalanceSnapshot.builder()
                .workspace(ctx.workspace())
                .wallet(ctx.cash())
                .dailyClosing(closing)
                .snapshotDate(closing.getClosingDate())
                .balance(new BigDecimal("130.00"))
                .ledgerBalance(new BigDecimal("100.00"))
                .difference(new BigDecimal("30.00"))
                .sourceType(BalanceSourceType.MANUAL)
                .reconciliationStatus(ReconciliationStatus.ADJUSTED)
                .adjustmentTransaction(adjustment)
                .createdBy(ctx.user())
                .build());
    }

    private ObligationOccurrence confirmedOccurrence(
            TestContext ctx,
            Transaction transaction,
            String actualAmount,
            ObligationDirection direction) {
        RecurringObligationTemplate template = templateRepository.saveAndFlush(RecurringObligationTemplate.builder()
                .workspace(ctx.workspace())
                .name("Rent " + UUID.randomUUID())
                .direction(direction)
                .amountMode(ObligationAmountMode.VARIABLE)
                .frequency(ObligationFrequency.MONTHLY)
                .intervalCount(1)
                .startDate(LocalDate.of(2026, 7, 1))
                .createdByUser(ctx.user())
                .build());
        return occurrenceRepository.saveAndFlush(ObligationOccurrence.builder()
                .workspace(ctx.workspace())
                .template(template)
                .periodKey("2026-07-" + UUID.randomUUID().toString().substring(0, 8))
                .dueDate(LocalDate.of(2026, 7, 20))
                .actualAmount(new BigDecimal(actualAmount))
                .status(ObligationOccurrenceStatus.CONFIRMED)
                .linkedTransaction(transaction)
                .completedAt(Instant.parse("2026-07-20T10:00:00Z"))
                .build());
    }

    private TestContext context(String username) {
        User user = user(username);
        Workspace workspace = workspaceRepository.saveAndFlush(Workspace.builder()
                .name("Workspace " + username)
                .createdByUser(user)
                .build());
        workspaceMemberRepository.saveAndFlush(WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(WorkspaceRole.OWNER)
                .memberStatus("ACTIVE")
                .build());
        Wallet cash = wallet(workspace, "Cash");
        Wallet bank = wallet(workspace, "Bank");
        Category expense = category(workspace, "Food", CategoryType.EXPENSE);
        Category income = category(workspace, "Salary", CategoryType.INCOME);
        return new TestContext(user, workspace, cash, bank, expense, income);
    }

    private User user(String username) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.saveAndFlush(User.builder()
                .username((username + "_" + suffix).substring(0, Math.min(40, username.length() + 9)))
                .email(username + suffix + "@example.test")
                .fullName("User " + username)
                .build());
    }

    private Wallet wallet(Workspace workspace, String name) {
        return walletRepository.saveAndFlush(Wallet.builder()
                .workspace(workspace)
                .name(name + " " + UUID.randomUUID())
                .walletType(WalletType.CASH)
                .openingBalance(BigDecimal.ZERO)
                .openingDate(LocalDate.of(2026, 7, 1))
                .build());
    }

    private Category category(Workspace workspace, String name, CategoryType type) {
        return categoryRepository.saveAndFlush(Category.builder()
                .workspace(workspace)
                .name(name + " " + UUID.randomUUID())
                .categoryType(type)
                .build());
    }

    private record TestContext(
            User user,
            Workspace workspace,
            Wallet cash,
            Wallet bank,
            Category expenseCategory,
            Category incomeCategory) {
    }
}
