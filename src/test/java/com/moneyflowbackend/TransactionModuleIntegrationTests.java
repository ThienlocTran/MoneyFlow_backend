package com.moneyflowbackend;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.transaction.audit.TransactionAuditLogRepository;
import com.moneyflowbackend.transaction.audit.TransactionAuditService;
import com.moneyflowbackend.transaction.dto.TransactionPageResponse;
import com.moneyflowbackend.transaction.dto.TransactionRequest;
import com.moneyflowbackend.transaction.dto.TransactionResponse;
import com.moneyflowbackend.transaction.model.TransactionSourceType;
import com.moneyflowbackend.transaction.model.TransactionStatus;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.transaction.repository.TransferDetailRepository;
import com.moneyflowbackend.transaction.service.TransactionService;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.model.WalletType;
import com.moneyflowbackend.wallet.repository.WalletRepository;
import com.moneyflowbackend.wallet.service.WalletService;
import com.moneyflowbackend.workspace.model.PersonKind;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspacePerson;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspacePersonRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TransactionModuleIntegrationTests {

    @Autowired TransactionService transactionService;
    @Autowired WalletService walletService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired WorkspacePersonRepository workspacePersonRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired TransferDetailRepository transferDetailRepository;
    @Autowired TransactionAuditLogRepository transactionAuditLogRepository;
    @Autowired TransactionAuditService transactionAuditService;

    @Test
    void incomeExpenseTransferUpdateDeleteRestoreDriveBalances() {
        TestContext ctx = createContext("tx_flow", WorkspaceRole.OWNER);
        Wallet cash = wallet(ctx, "Cash", WalletType.CASH, "0");
        Wallet bank = wallet(ctx, "Bank", WalletType.BANK, "0");
        Category incomeCategory = category(ctx, "Salary", CategoryType.INCOME, true, false);
        Category expenseCategory = category(ctx, "Food", CategoryType.EXPENSE, true, false);

        TransactionResponse income = transactionService.create(ctx.workspace().getId(), incomeReq("2000000", cash, incomeCategory, "Salary"), ctx.user().getId());
        TransactionResponse expense = transactionService.create(ctx.workspace().getId(), expenseReq("300000", cash, expenseCategory, "Breakfast", TransactionStatus.POSTED), ctx.user().getId());
        TransactionResponse plannedExpense = transactionService.create(ctx.workspace().getId(), expenseReq("100000", cash, expenseCategory, "Plan", TransactionStatus.PLANNED), ctx.user().getId());
        TransactionResponse transfer = transactionService.create(ctx.workspace().getId(), transferReq("500000", cash, bank, "Move", TransactionStatus.POSTED), ctx.user().getId());

        assertThat(transactionRepository.findById(income.getId()).orElseThrow().getCreatedByUser().getId()).isEqualTo(ctx.user().getId());
        assertThat(transactionRepository.findById(income.getId()).orElseThrow().getSourceType()).isEqualTo(TransactionSourceType.MANUAL);
        assertThat(transferDetailRepository.findById(transfer.getId())).isPresent();
        assertThat(walletService.calculateCurrentBalance(cash.getId())).isEqualByComparingTo("1200000");
        assertThat(walletService.calculateCurrentBalance(bank.getId())).isEqualByComparingTo("500000");

        TransactionRequest incomeUpdate = incomeReq("2500000", cash, incomeCategory, "Salary adjusted");
        transactionService.update(ctx.workspace().getId(), income.getId(), incomeUpdate, ctx.user().getId());
        assertThat(walletService.calculateCurrentBalance(cash.getId())).isEqualByComparingTo("1700000");

        TransactionRequest plannedToPosted = expenseReq("100000", cash, expenseCategory, "Plan", TransactionStatus.POSTED);
        transactionService.update(ctx.workspace().getId(), plannedExpense.getId(), plannedToPosted, ctx.user().getId());
        assertThat(walletService.calculateCurrentBalance(cash.getId())).isEqualByComparingTo("1600000");

        TransactionRequest transferUpdate = transferReq("400000", cash, bank, "Move less", TransactionStatus.POSTED);
        transactionService.update(ctx.workspace().getId(), transfer.getId(), transferUpdate, ctx.user().getId());
        assertThat(walletService.calculateCurrentBalance(cash.getId())).isEqualByComparingTo("1700000");
        assertThat(walletService.calculateCurrentBalance(bank.getId())).isEqualByComparingTo("400000");

        transactionService.delete(ctx.workspace().getId(), expense.getId(), ctx.user().getId());
        assertThat(walletService.calculateCurrentBalance(cash.getId())).isEqualByComparingTo("2000000");
        assertThat(transactionService.list(ctx.workspace().getId(), null, null, null, null, null, null, null, null, null, false, 0, 20, null, ctx.user().getId()).getContent())
                .extracting(TransactionResponse::getId)
                .doesNotContain(expense.getId());
        assertThat(transactionService.list(ctx.workspace().getId(), null, null, null, null, null, null, null, null, null, true, 0, 20, null, ctx.user().getId()).getContent())
                .extracting(TransactionResponse::getId)
                .contains(expense.getId());

        transactionService.restore(ctx.workspace().getId(), expense.getId(), ctx.user().getId());
        assertThat(walletService.calculateCurrentBalance(cash.getId())).isEqualByComparingTo("1700000");

        transactionService.delete(ctx.workspace().getId(), transfer.getId(), ctx.user().getId());
        assertThat(walletService.calculateCurrentBalance(cash.getId())).isEqualByComparingTo("2100000");
        assertThat(walletService.calculateCurrentBalance(bank.getId())).isEqualByComparingTo("0");

        transactionService.restore(ctx.workspace().getId(), transfer.getId(), ctx.user().getId());
        assertThat(walletService.calculateCurrentBalance(cash.getId())).isEqualByComparingTo("1700000");
        assertThat(walletService.calculateCurrentBalance(bank.getId())).isEqualByComparingTo("400000");
    }

    @Test
    void transactionMutationsWriteAuditSnapshotsAndOwnerOnlyAuditAccess() {
        TestContext owner = createContext("tx_audit", WorkspaceRole.OWNER);
        TestContext editor = createContext("tx_audit_editor", WorkspaceRole.EDITOR);
        TestContext viewer = createContext("tx_audit_viewer", WorkspaceRole.VIEWER);
        TestContext outsider = createContext("tx_audit_outsider", WorkspaceRole.OWNER);
        workspaceMemberRepository.save(WorkspaceMember.builder().workspace(owner.workspace()).user(editor.user()).role(WorkspaceRole.EDITOR).build());
        workspaceMemberRepository.save(WorkspaceMember.builder().workspace(owner.workspace()).user(viewer.user()).role(WorkspaceRole.VIEWER).build());
        Wallet cash = wallet(owner, "Cash", WalletType.CASH, "0");
        Category food = category(owner, "Food", CategoryType.EXPENSE, true, false);

        TransactionResponse created = transactionService.create(owner.workspace().getId(), expenseReq("100", cash, food, "Lunch", TransactionStatus.POSTED), owner.user().getId());
        transactionService.update(owner.workspace().getId(), created.getId(), expenseReq("150", cash, food, "Lunch updated", TransactionStatus.POSTED), owner.user().getId());
        transactionService.delete(owner.workspace().getId(), created.getId(), owner.user().getId());
        transactionService.restore(owner.workspace().getId(), created.getId(), owner.user().getId());

        var logs = transactionAuditLogRepository.findByWorkspaceIdAndTransactionIdOrderByCreatedAtAsc(owner.workspace().getId(), created.getId());
        assertThat(logs).hasSize(4);
        assertThat(logs).extracting(log -> log.getAction().name())
                .containsExactly("CREATE", "UPDATE", "DELETE", "RESTORE");
        assertThat(logs.get(0).getBeforeData()).isNull();
        assertThat(new BigDecimal(logs.get(0).getAfterData().get("amount").toString())).isEqualByComparingTo("100.00");
        assertThat(logs.get(1).getBeforeData()).containsEntry("description", "Lunch");
        assertThat(logs.get(1).getAfterData()).containsEntry("description", "Lunch updated");
        assertThat(logs.get(2).getBeforeData()).containsEntry("deletedAt", null);
        assertThat(logs.get(2).getAfterData().get("deletedAt")).isNotNull();
        assertThat(logs.get(0).getAfterData()).doesNotContainKeys("audioUrl", "storagePublicId", "password", "token");

        assertThat(transactionAuditService.list(owner.workspace().getId(), created.getId(), owner.user().getId()))
                .extracting("action")
                .containsExactly("CREATE", "UPDATE", "SOFT_DELETE", "RESTORE");
        assertBusinessCode(() -> transactionAuditService.list(owner.workspace().getId(), created.getId(), editor.user().getId()), "FORBIDDEN");
        assertBusinessCode(() -> transactionAuditService.list(owner.workspace().getId(), created.getId(), viewer.user().getId()), "FORBIDDEN");
        assertBusinessCode(() -> transactionAuditService.list(owner.workspace().getId(), created.getId(), outsider.user().getId()), "WORKSPACE_ACCESS_DENIED");
    }

    @Test
    void failedCreateAndViewerMutationsDoNotWriteAudit() {
        TestContext owner = createContext("tx_audit_fail", WorkspaceRole.OWNER);
        TestContext viewer = createContext("tx_audit_fail_viewer", WorkspaceRole.VIEWER);
        workspaceMemberRepository.save(WorkspaceMember.builder().workspace(owner.workspace()).user(viewer.user()).role(WorkspaceRole.VIEWER).build());
        Wallet cash = wallet(owner, "Cash", WalletType.CASH, "0");
        Category food = category(owner, "Food", CategoryType.EXPENSE, true, false);
        long before = transactionAuditLogRepository.count();

        assertBusinessCode(() -> transactionService.create(owner.workspace().getId(), expenseReq("0", cash, food, "Bad", TransactionStatus.POSTED), owner.user().getId()), "INVALID_AMOUNT");
        assertBusinessCode(() -> transactionService.create(owner.workspace().getId(), expenseReq("1", cash, food, "Viewer", TransactionStatus.POSTED), viewer.user().getId()), "FORBIDDEN");

        TransactionResponse tx = transactionService.create(owner.workspace().getId(), expenseReq("1", cash, food, "Owner", TransactionStatus.POSTED), owner.user().getId());
        assertBusinessCode(() -> transactionService.update(owner.workspace().getId(), tx.getId(), expenseReq("2", cash, food, "Viewer", TransactionStatus.POSTED), viewer.user().getId()), "FORBIDDEN");
        assertBusinessCode(() -> transactionService.delete(owner.workspace().getId(), tx.getId(), viewer.user().getId()), "FORBIDDEN");
        transactionService.delete(owner.workspace().getId(), tx.getId(), owner.user().getId());
        assertBusinessCode(() -> transactionService.restore(owner.workspace().getId(), tx.getId(), viewer.user().getId()), "FORBIDDEN");

        assertThat(transactionAuditLogRepository.count()).isEqualTo(before + 2);
    }

    @Test
    void validationRulesRejectInvalidManualTransactions() {
        TestContext ctx = createContext("tx_validation", WorkspaceRole.OWNER);
        TestContext other = createContext("tx_other", WorkspaceRole.OWNER);
        Wallet cash = wallet(ctx, "Cash", WalletType.CASH, "0");
        Wallet bank = wallet(ctx, "Bank", WalletType.BANK, "0");
        Wallet inactiveWallet = wallet(ctx, "Inactive", WalletType.CASH, "0");
        inactiveWallet.setActive(false);
        walletRepository.saveAndFlush(inactiveWallet);
        Wallet otherWallet = wallet(other, "Other", WalletType.CASH, "0");
        Category incomeCategory = category(ctx, "Salary", CategoryType.INCOME, true, false);
        Category expenseCategory = category(ctx, "Food", CategoryType.EXPENSE, true, false);
        Category inactiveCategory = category(ctx, "Old", CategoryType.EXPENSE, false, false);
        Category archivedCategory = category(ctx, "Archived", CategoryType.EXPENSE, true, true);

        assertBusinessCode(() -> transactionService.create(ctx.workspace().getId(), req(TransactionType.ADJUSTMENT, "1"), ctx.user().getId()), "INVALID_TRANSACTION_TYPE");
        assertBusinessCode(() -> transactionService.create(ctx.workspace().getId(), expenseReq("0", cash, expenseCategory, "Bad", TransactionStatus.POSTED), ctx.user().getId()), "INVALID_AMOUNT");
        assertBusinessCode(() -> transactionService.create(ctx.workspace().getId(), expenseReq("1", cash, incomeCategory, "Bad", TransactionStatus.POSTED), ctx.user().getId()), "CATEGORY_TYPE_MISMATCH");
        assertBusinessCode(() -> transactionService.create(ctx.workspace().getId(), expenseReq("1", cash, inactiveCategory, "Bad", TransactionStatus.POSTED), ctx.user().getId()), "CATEGORY_INACTIVE");
        assertBusinessCode(() -> transactionService.create(ctx.workspace().getId(), expenseReq("1", cash, archivedCategory, "Bad", TransactionStatus.POSTED), ctx.user().getId()), "CATEGORY_ARCHIVED");
        assertBusinessCode(() -> transactionService.create(ctx.workspace().getId(), expenseReq("1", inactiveWallet, expenseCategory, "Bad", TransactionStatus.POSTED), ctx.user().getId()), "WALLET_INACTIVE");
        assertBusinessCode(() -> transactionService.create(ctx.workspace().getId(), expenseReq("1", otherWallet, expenseCategory, "Bad", TransactionStatus.POSTED), ctx.user().getId()), "WALLET_NOT_FOUND");
        TransactionRequest noWalletIncome = incomeReq("1", cash, incomeCategory, "No wallet");
        noWalletIncome.setWalletId(null);
        assertBusinessCode(() -> transactionService.create(ctx.workspace().getId(), noWalletIncome, ctx.user().getId()), "WALLET_NOT_FOUND");
        TransactionRequest plannedNoWallet = expenseReq("1", cash, expenseCategory, "Planned no wallet", TransactionStatus.PLANNED);
        plannedNoWallet.setWalletId(null);
        assertBusinessCode(() -> transactionService.create(ctx.workspace().getId(), plannedNoWallet, ctx.user().getId()), "WALLET_NOT_FOUND");
        TransactionRequest plannedNoCategory = expenseReq("1", cash, expenseCategory, "Planned no category", TransactionStatus.PLANNED);
        plannedNoCategory.setCategoryId(null);
        assertBusinessCode(() -> transactionService.create(ctx.workspace().getId(), plannedNoCategory, ctx.user().getId()), "CATEGORY_NOT_FOUND");

        TransactionRequest sameWallet = transferReq("1", cash, cash, "Bad", TransactionStatus.POSTED);
        assertBusinessCode(() -> transactionService.create(ctx.workspace().getId(), sameWallet, ctx.user().getId()), "TRANSFER_SAME_WALLET");
        TransactionRequest transferWithCategory = transferReq("1", cash, bank, "Bad", TransactionStatus.POSTED);
        transferWithCategory.setCategoryId(expenseCategory.getId());
        assertBusinessCode(() -> transactionService.create(ctx.workspace().getId(), transferWithCategory, ctx.user().getId()), "TRANSFER_CATEGORY_NOT_ALLOWED");

        TransactionResponse income = transactionService.create(ctx.workspace().getId(), incomeReq("10", cash, incomeCategory, "Income"), ctx.user().getId());
        assertBusinessCode(() -> transactionService.update(ctx.workspace().getId(), income.getId(), expenseReq("10", cash, expenseCategory, "Type flip", TransactionStatus.POSTED), ctx.user().getId()), "TRANSACTION_TYPE_IMMUTABLE");
    }

    @Test
    void internalHistoricalExcelMigrationAllowsNoWalletAndIsIdempotent() {
        TestContext ctx = createContext("tx_hist", WorkspaceRole.OWNER);
        Category incomeCategory = category(ctx, "Salary", CategoryType.INCOME, true, false);
        TransactionRequest req = req(TransactionType.INCOME, "7553000");
        req.setCategoryId(incomeCategory.getId());
        req.setTransactionDate(LocalDate.of(2026, 1, 31));
        req.setDescription("T1 income");

        TransactionResponse first = transactionService.createHistoricalExcelMigration(
                ctx.workspace().getId(), req, ctx.user().getId(), "hist-key-1", "T1!A57", "raw");
        TransactionResponse second = transactionService.createHistoricalExcelMigration(
                ctx.workspace().getId(), req, ctx.user().getId(), "hist-key-1", "T1!A57", "raw");

        assertThat(second.getId()).isEqualTo(first.getId());
        var row = transactionRepository.findById(first.getId()).orElseThrow();
        assertThat(row.getWallet()).isNull();
        assertThat(row.getSourceType()).isEqualTo(TransactionSourceType.EXCEL_MIGRATION);
        assertThat(row.isHistorical()).isTrue();
        assertThat(row.isAffectsWalletBalance()).isFalse();
        assertThat(row.isWalletUnknown()).isTrue();
        assertThat(transactionRepository.findAll().stream().filter(tx -> "hist-key-1".equals(tx.getMigrationKey())).count()).isEqualTo(1);
    }

    @Test
    void listFiltersAndAuthorizationWork() {
        TestContext owner = createContext("tx_list", WorkspaceRole.OWNER);
        TestContext viewer = createContext("tx_viewer", WorkspaceRole.VIEWER);
        TestContext outsider = createContext("tx_outsider", WorkspaceRole.OWNER);
        workspaceMemberRepository.save(WorkspaceMember.builder().workspace(owner.workspace()).user(viewer.user()).role(WorkspaceRole.VIEWER).build());
        Wallet cash = wallet(owner, "Cash", WalletType.CASH, "0");
        Wallet bank = wallet(owner, "Bank", WalletType.BANK, "0");
        Category incomeCategory = category(owner, "Salary", CategoryType.INCOME, true, false);
        Category expenseCategory = category(owner, "Food", CategoryType.EXPENSE, true, false);
        WorkspacePerson person = person(owner, "Family");

        TransactionRequest oldIncomeReq = incomeReq("100", cash, incomeCategory, "old salary");
        oldIncomeReq.setTransactionDate(LocalDate.of(2026, 6, 1));
        TransactionResponse oldIncome = transactionService.create(owner.workspace().getId(), oldIncomeReq, owner.user().getId());
        TransactionRequest lunchReq = expenseReq("50", cash, expenseCategory, "Lunch", TransactionStatus.POSTED);
        lunchReq.setNote("spicy noodles");
        lunchReq.setTransactionDate(LocalDate.of(2026, 6, 2));
        lunchReq.setAttributedPersonId(person.getId());
        TransactionResponse lunch = transactionService.create(owner.workspace().getId(), lunchReq, owner.user().getId());
        TransactionRequest transferReq = transferReq("20", cash, bank, "cash to bank", TransactionStatus.POSTED);
        transferReq.setTransactionDate(LocalDate.of(2026, 6, 3));
        TransactionResponse transfer = transactionService.create(owner.workspace().getId(), transferReq, owner.user().getId());
        transactionService.delete(owner.workspace().getId(), oldIncome.getId(), owner.user().getId());

        assertThat(transactionService.list(owner.workspace().getId(), null, null, TransactionType.EXPENSE, null, null, null, null, null, null, false, 0, 20, null, owner.user().getId()).getContent())
                .extracting(TransactionResponse::getId).containsExactly(lunch.getId());
        assertThat(transactionService.list(owner.workspace().getId(), null, null, null, TransactionStatus.POSTED, null, null, null, null, null, false, 0, 20, null, owner.user().getId()).getContent())
                .extracting(TransactionResponse::getId).contains(transfer.getId(), lunch.getId());
        assertThat(transactionService.list(owner.workspace().getId(), null, null, null, null, cash.getId(), null, null, null, null, false, 0, 20, null, owner.user().getId()).getContent())
                .extracting(TransactionResponse::getId).contains(transfer.getId(), lunch.getId());
        assertThat(transactionService.list(owner.workspace().getId(), null, null, null, null, bank.getId(), null, null, null, null, false, 0, 20, null, owner.user().getId()).getContent())
                .extracting(TransactionResponse::getId).containsExactly(transfer.getId());
        assertThat(transactionService.list(owner.workspace().getId(), null, null, null, null, null, expenseCategory.getId(), null, null, null, false, 0, 20, null, owner.user().getId()).getContent())
                .extracting(TransactionResponse::getId).containsExactly(lunch.getId());
        assertThat(transactionService.list(owner.workspace().getId(), null, null, null, null, null, null, person.getId(), null, null, false, 0, 20, null, owner.user().getId()).getContent())
                .extracting(TransactionResponse::getId).containsExactly(lunch.getId());
        assertThat(transactionService.list(owner.workspace().getId(), null, null, null, null, null, null, null, null, "noodles", false, 0, 20, null, owner.user().getId()).getContent())
                .extracting(TransactionResponse::getId).containsExactly(lunch.getId());
        assertThat(transactionService.list(owner.workspace().getId(), LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 3), null, null, null, null, null, null, null, false, 0, 1, null, owner.user().getId()))
                .satisfies(page -> {
                    assertThat(page.getContent()).hasSize(1);
                    assertThat(page.getTotalElements()).isEqualTo(2);
                });
        assertThat(transactionService.list(owner.workspace().getId(), null, null, null, null, null, null, null, null, null, true, 0, 20, null, owner.user().getId()).getContent())
                .extracting(TransactionResponse::getId).contains(oldIncome.getId());
        assertBusinessCode(() -> transactionService.list(owner.workspace().getId(), LocalDate.of(2026, 6, 4), LocalDate.of(2026, 6, 1), null, null, null, null, null, null, null, false, 0, 20, null, owner.user().getId()), "INVALID_DATE_RANGE");

        assertThat(transactionService.list(owner.workspace().getId(), null, null, null, null, null, null, null, null, null, false, 0, 20, null, viewer.user().getId()).getContent()).hasSize(2);
        assertBusinessCode(() -> transactionService.create(owner.workspace().getId(), incomeReq("1", cash, incomeCategory, "viewer"), viewer.user().getId()), "FORBIDDEN");
        assertBusinessCode(() -> transactionService.list(owner.workspace().getId(), null, null, null, null, null, null, null, null, null, false, 0, 20, null, outsider.user().getId()), "WORKSPACE_ACCESS_DENIED");
    }

    @Test
    void transactionFiltersSearchDeletedAndExportWorkSafely() {
        TestContext owner = createContext("tx_filter_export", WorkspaceRole.OWNER);
        TestContext editor = createContext("tx_filter_export_editor", WorkspaceRole.EDITOR);
        TestContext viewer = createContext("tx_filter_export_viewer", WorkspaceRole.VIEWER);
        TestContext outsider = createContext("tx_filter_export_outsider", WorkspaceRole.OWNER);
        workspaceMemberRepository.save(WorkspaceMember.builder().workspace(owner.workspace()).user(editor.user()).role(WorkspaceRole.EDITOR).build());
        workspaceMemberRepository.save(WorkspaceMember.builder().workspace(owner.workspace()).user(viewer.user()).role(WorkspaceRole.VIEWER).build());
        Wallet cash = wallet(owner, "Cash", WalletType.CASH, "0");
        Category food = category(owner, "Food", CategoryType.EXPENSE, true, false);

        TransactionRequest manualReq = expenseReq("12.50", cash, food, "Comma, quote \" meal", TransactionStatus.POSTED);
        manualReq.setTransactionDate(LocalDate.of(2026, 7, 2));
        manualReq.setNote("Comma, quote \" meal\nline two");
        TransactionResponse manual = transactionService.create(owner.workspace().getId(), manualReq, owner.user().getId());

        TransactionRequest quickReq = expenseReq("9", cash, food, "Quick lunch", TransactionStatus.POSTED);
        quickReq.setTransactionDate(LocalDate.of(2026, 7, 3));
        TransactionResponse quick = transactionService.createWithSource(owner.workspace().getId(), quickReq, editor.user().getId(),
                TransactionSourceType.QUICK_TEXT, "raw noodles from quick text", null);

        TransactionRequest deletedReq = expenseReq("5", cash, food, "Deleted", TransactionStatus.POSTED);
        deletedReq.setTransactionDate(LocalDate.of(2026, 7, 4));
        TransactionResponse deleted = transactionService.create(owner.workspace().getId(), deletedReq, owner.user().getId());
        transactionService.delete(owner.workspace().getId(), deleted.getId(), owner.user().getId());

        assertThat(transactionService.list(owner.workspace().getId(), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
                null, null, null, null, null, null, TransactionSourceType.QUICK_TEXT, null, null, false, 0, 20, null, owner.user().getId()).getContent())
                .extracting(TransactionResponse::getId).containsExactly(quick.getId());
        assertThat(transactionService.list(owner.workspace().getId(), null, null, null, null, null, null, null, null,
                "raw noodles", false, 0, 20, null, owner.user().getId()).getContent())
                .extracting(TransactionResponse::getId).containsExactly(quick.getId());
        assertThat(transactionService.list(owner.workspace().getId(), null, null, null, null, null, null, null, null,
                TransactionSourceType.QUICK_TEXT, editor.user().getId(), null, false, 0, 500, null, owner.user().getId()).getSize())
                .isEqualTo(100);
        assertThat(transactionService.list(owner.workspace().getId(), null, null, null, null, null, null, null, null,
                null, null, null, true, 0, 20, null, editor.user().getId()).getContent())
                .extracting(TransactionResponse::getId).contains(deleted.getId());
        assertBusinessCode(() -> transactionService.list(owner.workspace().getId(), null, null, null, null, null, null, null,
                null, null, null, null, true, 0, 20, null, viewer.user().getId()), "FORBIDDEN");

        String csv = new String(transactionService.exportCsv(owner.workspace().getId(), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
                null, null, null, null, null, null, null, null, "quote", false, owner.user().getId()), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFtransactionDate,type,amount");
        assertThat(csv).contains("\"Comma, quote \"\" meal\nline two\"");
        assertThat(csv).doesNotContain("storagePublicId", "playbackUrl", "audioUrl", "password", "refreshToken");

        assertBusinessCode(() -> transactionService.exportCsv(outsider.workspace().getId(), null, null, null, null, null, null,
                null, null, null, null, null, false, owner.user().getId()), "WORKSPACE_ACCESS_DENIED");
    }

    @Test
    void transferRepresentationDoesNotDoubleCountOrLeakInternalWallet() {
        TestContext ctx = createContext("tx_transfer_rep", WorkspaceRole.OWNER);
        Wallet cash = wallet(ctx, "Cash", WalletType.CASH, "1000");
        Wallet bank = wallet(ctx, "Bank", WalletType.BANK, "0");

        TransactionResponse transfer = transactionService.create(ctx.workspace().getId(), transferReq("500", cash, bank, "Move once", TransactionStatus.POSTED), ctx.user().getId());
        var txRow = transactionRepository.findById(transfer.getId()).orElseThrow();
        var detail = transferDetailRepository.findById(transfer.getId()).orElseThrow();

        assertThat(txRow.getTransactionType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(txRow.getWallet().getId()).isEqualTo(cash.getId());
        assertThat(txRow.getCategory()).isNull();
        assertThat(detail.getSourceWallet().getId()).isEqualTo(cash.getId());
        assertThat(detail.getDestinationWallet().getId()).isEqualTo(bank.getId());
        assertThat(transfer.getWallet()).isNull();
        assertThat(transfer.getCategory()).isNull();
        assertThat(transfer.getTransfer()).isNotNull();
        assertThat(transfer.getWalletId()).isNull();
        assertThat(walletService.calculateCurrentBalance(cash.getId())).isEqualByComparingTo("500");
        assertThat(walletService.calculateCurrentBalance(bank.getId())).isEqualByComparingTo("500");
        assertThat(walletService.calculateCurrentBalance(cash.getId()).add(walletService.calculateCurrentBalance(bank.getId()))).isEqualByComparingTo("1000");

        assertThat(transactionService.list(ctx.workspace().getId(), null, null, null, null, cash.getId(), null, null, null, null, false, 0, 20, null, ctx.user().getId()).getContent())
                .extracting(TransactionResponse::getId)
                .containsExactly(transfer.getId());
        assertThat(transactionService.list(ctx.workspace().getId(), null, null, null, null, bank.getId(), null, null, null, null, false, 0, 20, null, ctx.user().getId()).getContent())
                .extracting(TransactionResponse::getId)
                .containsExactly(transfer.getId());
    }

    @Test
    void plannedPostedTransitionsAndWalletCategoryUpdatesRecalculateBalances() {
        TestContext ctx = createContext("tx_update_rules", WorkspaceRole.OWNER);
        Wallet cash = wallet(ctx, "Cash", WalletType.CASH, "0");
        Wallet bank = wallet(ctx, "Bank", WalletType.BANK, "0");
        Category incomeCategory = category(ctx, "Salary", CategoryType.INCOME, true, false);
        Category expenseCategory = category(ctx, "Food", CategoryType.EXPENSE, true, false);
        Category otherExpenseCategory = category(ctx, "Transport", CategoryType.EXPENSE, true, false);

        TransactionRequest plannedIncomeReq = incomeReq("1000", cash, incomeCategory, "Planned salary");
        plannedIncomeReq.setStatus(TransactionStatus.PLANNED);
        TransactionResponse plannedIncome = transactionService.create(ctx.workspace().getId(), plannedIncomeReq, ctx.user().getId());
        assertThat(walletService.calculateCurrentBalance(cash.getId())).isEqualByComparingTo("0");
        plannedIncomeReq.setStatus(TransactionStatus.POSTED);
        transactionService.update(ctx.workspace().getId(), plannedIncome.getId(), plannedIncomeReq, ctx.user().getId());
        assertThat(walletService.calculateCurrentBalance(cash.getId())).isEqualByComparingTo("1000");
        plannedIncomeReq.setStatus(TransactionStatus.PLANNED);
        transactionService.update(ctx.workspace().getId(), plannedIncome.getId(), plannedIncomeReq, ctx.user().getId());
        assertThat(walletService.calculateCurrentBalance(cash.getId())).isEqualByComparingTo("0");

        TransactionResponse expense = transactionService.create(ctx.workspace().getId(), expenseReq("200", cash, expenseCategory, "Expense", TransactionStatus.POSTED), ctx.user().getId());
        assertThat(walletService.calculateCurrentBalance(cash.getId())).isEqualByComparingTo("-200");
        TransactionRequest moveExpense = expenseReq("250", bank, otherExpenseCategory, "Expense moved", TransactionStatus.POSTED);
        transactionService.update(ctx.workspace().getId(), expense.getId(), moveExpense, ctx.user().getId());
        assertThat(walletService.calculateCurrentBalance(cash.getId())).isEqualByComparingTo("0");
        assertThat(walletService.calculateCurrentBalance(bank.getId())).isEqualByComparingTo("-250");

        TransactionResponse plannedTransfer = transactionService.create(ctx.workspace().getId(), transferReq("100", cash, bank, "Plan transfer", TransactionStatus.PLANNED), ctx.user().getId());
        assertThat(walletService.calculateCurrentBalance(cash.getId())).isEqualByComparingTo("0");
        assertThat(walletService.calculateCurrentBalance(bank.getId())).isEqualByComparingTo("-250");
        transactionService.update(ctx.workspace().getId(), plannedTransfer.getId(), transferReq("100", cash, bank, "Post transfer", TransactionStatus.POSTED), ctx.user().getId());
        assertThat(walletService.calculateCurrentBalance(cash.getId())).isEqualByComparingTo("-100");
        assertThat(walletService.calculateCurrentBalance(bank.getId())).isEqualByComparingTo("-150");
    }

    @Test
    void transferValidationCoversInactiveCrossWorkspaceAndNoPartialRows() {
        TestContext ctx = createContext("tx_transfer_validation", WorkspaceRole.OWNER);
        TestContext other = createContext("tx_transfer_other", WorkspaceRole.OWNER);
        Wallet cash = wallet(ctx, "Cash", WalletType.CASH, "0");
        Wallet bank = wallet(ctx, "Bank", WalletType.BANK, "0");
        Wallet inactive = wallet(ctx, "Inactive", WalletType.BANK, "0");
        inactive.setActive(false);
        walletRepository.saveAndFlush(inactive);
        Wallet otherWallet = wallet(other, "Other", WalletType.BANK, "0");
        long txCount = transactionRepository.count();
        long detailCount = transferDetailRepository.count();

        assertBusinessCode(() -> transactionService.create(ctx.workspace().getId(), transferReq("10", inactive, bank, "Bad source", TransactionStatus.POSTED), ctx.user().getId()), "WALLET_INACTIVE");
        assertBusinessCode(() -> transactionService.create(ctx.workspace().getId(), transferReq("10", cash, inactive, "Bad dest", TransactionStatus.POSTED), ctx.user().getId()), "WALLET_INACTIVE");
        assertBusinessCode(() -> transactionService.create(ctx.workspace().getId(), transferReq("10", cash, otherWallet, "Bad cross", TransactionStatus.POSTED), ctx.user().getId()), "WALLET_NOT_FOUND");
        assertThat(transactionRepository.count()).isEqualTo(txCount);
        assertThat(transferDetailRepository.count()).isEqualTo(detailCount);
    }

    @Test
    void editorCanWriteAndCrossWorkspaceTransactionIdsAreDenied() {
        TestContext owner = createContext("tx_owner_auth", WorkspaceRole.OWNER);
        TestContext editor = createContext("tx_editor_auth", WorkspaceRole.EDITOR);
        TestContext outsider = createContext("tx_outsider_auth", WorkspaceRole.OWNER);
        workspaceMemberRepository.save(WorkspaceMember.builder().workspace(owner.workspace()).user(editor.user()).role(WorkspaceRole.EDITOR).build());
        Wallet cash = wallet(owner, "Cash", WalletType.CASH, "0");
        Category incomeCategory = category(owner, "Salary", CategoryType.INCOME, true, false);

        TransactionResponse created = transactionService.create(owner.workspace().getId(), incomeReq("10", cash, incomeCategory, "Editor write"), editor.user().getId());
        assertThat(walletService.calculateCurrentBalance(cash.getId())).isEqualByComparingTo("10");
        assertThat(transactionService.getDetails(owner.workspace().getId(), created.getId(), false, editor.user().getId()).getId()).isEqualTo(created.getId());
        assertBusinessCode(() -> transactionService.getDetails(outsider.workspace().getId(), created.getId(), false, outsider.user().getId()), "TRANSACTION_NOT_FOUND");
        assertBusinessCode(() -> transactionService.update(outsider.workspace().getId(), created.getId(), incomeReq("20", cash, incomeCategory, "Nope"), outsider.user().getId()), "TRANSACTION_NOT_FOUND");
    }

    @Test
    void restoreMissingTransferDetailIsRejected() {
        TestContext ctx = createContext("tx_restore", WorkspaceRole.OWNER);
        Wallet cash = wallet(ctx, "Cash", WalletType.CASH, "0");
        Wallet bank = wallet(ctx, "Bank", WalletType.BANK, "0");
        TransactionResponse transfer = transactionService.create(ctx.workspace().getId(), transferReq("10", cash, bank, "Move", TransactionStatus.POSTED), ctx.user().getId());
        transactionService.delete(ctx.workspace().getId(), transfer.getId(), ctx.user().getId());
        transferDetailRepository.deleteById(transfer.getId());
        transferDetailRepository.flush();

        assertBusinessCode(() -> transactionService.restore(ctx.workspace().getId(), transfer.getId(), ctx.user().getId()), "TRANSFER_DETAIL_NOT_FOUND");
    }

    @Test
    void restoreRejectsInactiveWalletOrCategory() {
        TestContext ctx = createContext("tx_restore_inactive", WorkspaceRole.OWNER);
        Wallet cash = wallet(ctx, "Cash", WalletType.CASH, "0");
        Wallet bank = wallet(ctx, "Bank", WalletType.BANK, "0");
        Category expenseCategory = category(ctx, "Food", CategoryType.EXPENSE, true, false);

        TransactionResponse expense = transactionService.create(ctx.workspace().getId(), expenseReq("10", cash, expenseCategory, "Food", TransactionStatus.POSTED), ctx.user().getId());
        transactionService.delete(ctx.workspace().getId(), expense.getId(), ctx.user().getId());
        cash.setActive(false);
        walletRepository.saveAndFlush(cash);
        assertBusinessCode(() -> transactionService.restore(ctx.workspace().getId(), expense.getId(), ctx.user().getId()), "WALLET_INACTIVE");

        cash.setActive(true);
        walletRepository.saveAndFlush(cash);
        expenseCategory.setActive(false);
        categoryRepository.saveAndFlush(expenseCategory);
        assertBusinessCode(() -> transactionService.restore(ctx.workspace().getId(), expense.getId(), ctx.user().getId()), "CATEGORY_INACTIVE");

        expenseCategory.setActive(true);
        categoryRepository.saveAndFlush(expenseCategory);
        TransactionResponse transfer = transactionService.create(ctx.workspace().getId(), transferReq("5", cash, bank, "Move", TransactionStatus.POSTED), ctx.user().getId());
        transactionService.delete(ctx.workspace().getId(), transfer.getId(), ctx.user().getId());
        bank.setActive(false);
        walletRepository.saveAndFlush(bank);
        assertBusinessCode(() -> transactionService.restore(ctx.workspace().getId(), transfer.getId(), ctx.user().getId()), "WALLET_INACTIVE");
    }

    private TestContext createContext(String prefix, WorkspaceRole role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .fullName("Transaction Test User")
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

    private Wallet wallet(TestContext ctx, String name, WalletType type, String openingBalance) {
        return walletRepository.saveAndFlush(Wallet.builder()
                .workspace(ctx.workspace())
                .name(name)
                .walletType(type)
                .openingBalance(bd(openingBalance))
                .isActive(true)
                .includeInTotal(true)
                .build());
    }

    private Category category(TestContext ctx, String name, CategoryType type, boolean active, boolean archived) {
        return categoryRepository.saveAndFlush(Category.builder()
                .workspace(ctx.workspace())
                .name(name)
                .categoryType(type)
                .isActive(active)
                .isArchived(archived)
                .build());
    }

    private WorkspacePerson person(TestContext ctx, String name) {
        return workspacePersonRepository.saveAndFlush(WorkspacePerson.builder()
                .workspace(ctx.workspace())
                .displayName(name)
                .personKind(PersonKind.MEMBER)
                .isActive(true)
                .build());
    }

    private TransactionRequest req(TransactionType type, String amount) {
        TransactionRequest req = new TransactionRequest();
        req.setType(type);
        req.setStatus(TransactionStatus.POSTED);
        req.setAmount(bd(amount));
        req.setTransactionDate(LocalDate.of(2026, 6, 15));
        return req;
    }

    private TransactionRequest incomeReq(String amount, Wallet wallet, Category category, String description) {
        TransactionRequest req = req(TransactionType.INCOME, amount);
        req.setWalletId(wallet.getId());
        req.setCategoryId(category.getId());
        req.setDescription(description);
        return req;
    }

    private TransactionRequest expenseReq(String amount, Wallet wallet, Category category, String description, TransactionStatus status) {
        TransactionRequest req = req(TransactionType.EXPENSE, amount);
        req.setStatus(status);
        req.setWalletId(wallet.getId());
        req.setCategoryId(category.getId());
        req.setDescription(description);
        return req;
    }

    private TransactionRequest transferReq(String amount, Wallet source, Wallet destination, String description, TransactionStatus status) {
        TransactionRequest req = req(TransactionType.TRANSFER, amount);
        req.setStatus(status);
        req.setSourceWalletId(source.getId());
        req.setDestinationWalletId(destination.getId());
        req.setDescription(description);
        return req;
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
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

