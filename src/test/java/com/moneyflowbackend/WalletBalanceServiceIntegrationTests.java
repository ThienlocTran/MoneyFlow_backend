package com.moneyflowbackend;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
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
import com.moneyflowbackend.wallet.service.WalletBalanceService;
import com.moneyflowbackend.wallet.service.WalletService;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WalletBalanceServiceIntegrationTests {
    @Autowired WalletBalanceService walletBalanceService;
    @Autowired WalletService walletService;
    @Autowired WalletRepository walletRepository;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired TransferDetailRepository transferDetailRepository;

    @Test
    void currentBalanceAppliesLedgerEffectsAndFiltersUnsafeRows() {
        TestContext ctx = context("balance_effects");
        TestContext other = context("balance_other");
        LocalDate openingDate = LocalDate.of(2026, 6, 10);
        Wallet wallet = wallet(ctx, "Cash", "1000", openingDate);
        Wallet peer = wallet(ctx, "Bank", "200", openingDate);

        tx(ctx, wallet, TransactionType.INCOME, "999", openingDate.minusDays(1), TransactionStatus.POSTED);
        tx(ctx, wallet, TransactionType.INCOME, "100", openingDate, TransactionStatus.POSTED);
        tx(ctx, wallet, TransactionType.EXPENSE, "20", openingDate, TransactionStatus.POSTED);
        tx(ctx, wallet, TransactionType.LOAN_DISBURSEMENT, "30", openingDate, TransactionStatus.POSTED);
        tx(ctx, wallet, TransactionType.LOAN_COLLECTION, "40", openingDate, TransactionStatus.POSTED);
        tx(ctx, wallet, TransactionType.BORROWING_RECEIPT, "50", openingDate, TransactionStatus.POSTED);
        tx(ctx, wallet, TransactionType.BORROWING_REPAYMENT, "60", openingDate, TransactionStatus.POSTED);
        adjustment(ctx, wallet, "70", openingDate, AdjustmentDirection.INCREASE);
        adjustment(ctx, wallet, "80", openingDate, AdjustmentDirection.DECREASE);
        adjustment(ctx, wallet, "999", openingDate, null);
        transfer(ctx, wallet, peer, "10", openingDate, TransactionStatus.POSTED);
        transfer(ctx, peer, wallet, "15", openingDate, TransactionStatus.POSTED);

        tx(ctx, wallet, TransactionType.INCOME, "999", openingDate, TransactionStatus.PLANNED);
        tx(ctx, wallet, TransactionType.INCOME, "999", openingDate, TransactionStatus.DRAFT);
        tx(ctx, wallet, TransactionType.INCOME, "999", openingDate, TransactionStatus.VOID);
        tx(ctx, wallet, TransactionType.INCOME, "999", openingDate, TransactionStatus.POSTED).setDeletedAt(Instant.now());
        Transaction ignored = tx(ctx, wallet, TransactionType.INCOME, "999", openingDate, TransactionStatus.POSTED);
        ignored.setAffectsWalletBalance(false);
        transactionRepository.saveAndFlush(ignored);
        tx(other, wallet, TransactionType.INCOME, "999", openingDate, TransactionStatus.POSTED);

        assertThat(walletBalanceService.calculateCurrentBalance(wallet.getId())).isEqualByComparingTo("1075");
        assertThat(walletService.calculateCurrentBalance(wallet.getId())).isEqualByComparingTo("1075");
        assertThat(walletBalanceService.calculateCurrentBalances(List.of(wallet, peer)))
                .containsEntry(wallet.getId(), new BigDecimal("1075.00"))
                .containsEntry(peer.getId(), new BigDecimal("195.00"));
    }

    @Test
    void endOfDayBalanceUsesBusinessDateCutoffAndCurrentBalanceStillIncludesLaterRows() {
        TestContext ctx = context("balance_cutoff");
        LocalDate day10 = LocalDate.of(2026, 6, 10);
        Wallet wallet = wallet(ctx, "Cash", "100", day10.minusDays(1));
        Wallet peer = wallet(ctx, "Bank", "0", day10.minusDays(1));

        tx(ctx, wallet, TransactionType.INCOME, "10", day10, null, TransactionStatus.POSTED);
        tx(ctx, wallet, TransactionType.INCOME, "20", day10, LocalTime.of(23, 59), TransactionStatus.POSTED);
        tx(ctx, wallet, TransactionType.EXPENSE, "5", day10, LocalTime.of(1, 0), TransactionStatus.POSTED);
        transfer(ctx, peer, wallet, "7", day10, TransactionStatus.POSTED);
        tx(ctx, wallet, TransactionType.INCOME, "1000", day10.plusDays(1), null, TransactionStatus.POSTED);
        transfer(ctx, wallet, peer, "50", day10.plusDays(1), TransactionStatus.POSTED);

        assertThat(walletBalanceService.calculateBalanceAtEndOfDay(wallet.getId(), day10)).isEqualByComparingTo("132");
        assertThat(walletBalanceService.calculateBalancesAtEndOfDay(List.of(wallet, peer), day10))
                .containsEntry(wallet.getId(), new BigDecimal("132.00"))
                .containsEntry(peer.getId(), new BigDecimal("-7.00"));
        assertThat(walletBalanceService.calculateCurrentBalance(wallet.getId())).isEqualByComparingTo("1082");
    }

    private TestContext context(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .fullName("Wallet Balance Test")
                .build());
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .name(prefix + " workspace")
                .createdByUser(user)
                .build());
        return new TestContext(user, workspace);
    }

    private Wallet wallet(TestContext ctx, String name, String openingBalance, LocalDate openingDate) {
        return walletRepository.saveAndFlush(Wallet.builder()
                .workspace(ctx.workspace())
                .name(name)
                .walletType(WalletType.CASH)
                .openingBalance(new BigDecimal(openingBalance))
                .openingDate(openingDate)
                .isActive(true)
                .includeInTotal(true)
                .build());
    }

    private Transaction tx(TestContext ctx, Wallet wallet, TransactionType type, String amount, LocalDate date, TransactionStatus status) {
        return tx(ctx, wallet, type, amount, date, null, status);
    }

    private Transaction tx(TestContext ctx, Wallet wallet, TransactionType type, String amount, LocalDate date, LocalTime time, TransactionStatus status) {
        return transactionRepository.saveAndFlush(Transaction.builder()
                .workspace(ctx.workspace())
                .createdByUser(ctx.user())
                .wallet(wallet)
                .transactionType(type)
                .transactionStatus(status)
                .amount(new BigDecimal(amount))
                .transactionDate(date)
                .transactionTime(time)
                .walletUnknown(false)
                .affectsWalletBalance(true)
                .build());
    }

    private Transaction adjustment(TestContext ctx, Wallet wallet, String amount, LocalDate date, AdjustmentDirection direction) {
        return transactionRepository.saveAndFlush(Transaction.builder()
                .workspace(ctx.workspace())
                .createdByUser(ctx.user())
                .wallet(wallet)
                .transactionType(TransactionType.ADJUSTMENT)
                .adjustmentDirection(direction)
                .transactionStatus(TransactionStatus.POSTED)
                .amount(new BigDecimal(amount))
                .transactionDate(date)
                .walletUnknown(false)
                .affectsWalletBalance(true)
                .build());
    }

    private void transfer(TestContext ctx, Wallet source, Wallet destination, String amount, LocalDate date, TransactionStatus status) {
        Transaction tx = tx(ctx, source, TransactionType.TRANSFER, amount, date, status);
        transferDetailRepository.saveAndFlush(TransferDetail.builder()
                .transactionId(tx.getId())
                .transaction(tx)
                .sourceWallet(source)
                .destinationWallet(destination)
                .build());
    }

    private record TestContext(User user, Workspace workspace) {}
}
