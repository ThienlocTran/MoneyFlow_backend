package com.moneyflowbackend;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.obligation.model.ObligationAmountMode;
import com.moneyflowbackend.obligation.model.ObligationDirection;
import com.moneyflowbackend.obligation.model.ObligationFrequency;
import com.moneyflowbackend.obligation.model.ObligationOccurrence;
import com.moneyflowbackend.obligation.model.ObligationOccurrenceStatus;
import com.moneyflowbackend.obligation.model.RecurringObligationTemplate;
import com.moneyflowbackend.obligation.repository.ObligationOccurrenceRepository;
import com.moneyflowbackend.obligation.repository.RecurringObligationTemplateRepository;
import com.moneyflowbackend.planning.dto.ActuallySpendableResponse;
import com.moneyflowbackend.planning.model.PlanningHorizon;
import com.moneyflowbackend.planning.service.PlanningService;
import com.moneyflowbackend.studentloan.model.StudentLoan;
import com.moneyflowbackend.studentloan.model.StudentLoanStatus;
import com.moneyflowbackend.studentloan.repository.StudentLoanRepository;
import com.moneyflowbackend.transaction.model.Transaction;
import com.moneyflowbackend.transaction.model.TransactionStatus;
import com.moneyflowbackend.transaction.model.TransactionType;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlanningCommitmentIntegrationTests {
    @Autowired PlanningService planningService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired RecurringObligationTemplateRepository templateRepository;
    @Autowired ObligationOccurrenceRepository occurrenceRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired StudentLoanRepository studentLoanRepository;

    @Test
    void subtractsOnlyPendingPayableKnownOccurrencesInsideHorizon() {
        TestContext ctx = createContext("planning_obligations");
        Wallet wallet = wallet(ctx, "Cash", "1000");
        RecurringObligationTemplate payable = template(ctx, ObligationDirection.PAYABLE, "Rent");
        RecurringObligationTemplate receivable = template(ctx, ObligationDirection.RECEIVABLE, "Salary");
        occurrence(ctx, payable, "start", LocalDate.of(2026, 8, 1), "100", ObligationOccurrenceStatus.PENDING, null);
        occurrence(ctx, payable, "end", LocalDate.of(2026, 8, 31), "200", ObligationOccurrenceStatus.PENDING, null);
        occurrence(ctx, payable, "outside", LocalDate.of(2026, 9, 1), "300", ObligationOccurrenceStatus.PENDING, null);
        occurrence(ctx, receivable, "receivable", LocalDate.of(2026, 8, 10), "400", ObligationOccurrenceStatus.PENDING, null);
        Transaction tx = transaction(ctx, wallet, "500", LocalDate.of(2026, 8, 15));
        occurrence(ctx, payable, "confirmed", LocalDate.of(2026, 8, 15), "500", ObligationOccurrenceStatus.CONFIRMED, tx);

        ActuallySpendableResponse response = planningService.actuallySpendable(
                ctx.workspace().getId(),
                ctx.user().getId(),
                PlanningHorizon.CUSTOM,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                null);

        assertThat(response.commitmentBreakdown().knownUpcomingObligations()).isEqualByComparingTo("300");
        assertThat(response.commitmentBreakdown().variableUnknownCount()).isZero();
        assertThat(response.actuallySpendable()).isEqualByComparingTo("200");
    }

    @Test
    void nullExpectedAmountIsIncompleteAndExcludedFromKnownSum() {
        TestContext ctx = createContext("planning_variable");
        wallet(ctx, "Cash", "1000");
        RecurringObligationTemplate payable = template(ctx, ObligationDirection.PAYABLE, "Utilities");
        ObligationOccurrence variable = occurrence(ctx, payable, "variable", LocalDate.of(2026, 8, 5), null, ObligationOccurrenceStatus.PENDING, null);
        occurrence(ctx, payable, "known", LocalDate.of(2026, 8, 6), "125", ObligationOccurrenceStatus.PENDING, null);

        ActuallySpendableResponse response = planningService.actuallySpendable(
                ctx.workspace().getId(),
                ctx.user().getId(),
                PlanningHorizon.CUSTOM,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                null);

        assertThat(response.commitmentBreakdown().knownUpcomingObligations()).isEqualByComparingTo("125");
        assertThat(response.commitmentBreakdown().variableUnknownCount()).isEqualTo(1);
        assertThat(response.incomplete()).isTrue();
        assertThat(response.warnings()).anyMatch(text -> text.contains(variable.getId().toString()) && text.contains("2026-08-05"));
    }

    @Test
    void activeStudentLoansAreAdvisoryAndNotDeducted() {
        TestContext ctx = createContext("planning_loans");
        wallet(ctx, "Cash", "1000");
        StudentLoan active = loan(ctx, "Federal", "125", "25", StudentLoanStatus.ACTIVE);
        loan(ctx, "Paused", "500", "50", StudentLoanStatus.PAUSED);

        ActuallySpendableResponse response = planningService.actuallySpendable(ctx.workspace().getId(), ctx.user().getId(), null, null, null, null);

        assertThat(response.advisoryCommitments().includedInActuallySpendable()).isFalse();
        assertThat(response.advisoryCommitments().total()).isEqualByComparingTo("150");
        assertThat(response.advisoryCommitments().studentLoans()).singleElement().satisfies(loan -> {
            assertThat(loan.loanId()).isEqualTo(active.getId());
            assertThat(loan.name()).isEqualTo("Federal");
            assertThat(loan.minimumMonthlyPayment()).isEqualByComparingTo("125");
            assertThat(loan.plannedExtraMonthlyPayment()).isEqualByComparingTo("25");
            assertThat(loan.advisoryTotal()).isEqualByComparingTo("150");
        });
        assertThat(response.actuallySpendable()).isEqualByComparingTo("1000");
        assertThat(response.assumptions()).anyMatch(text -> text.contains("Student loan advisory commitments are not included"));
    }

    private TestContext createContext(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .fullName("Planning Commitment User")
                .build());
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .name(prefix + " workspace")
                .createdByUser(user)
                .build());
        workspaceMemberRepository.save(WorkspaceMember.builder().workspace(workspace).user(user).role(WorkspaceRole.OWNER).build());
        return new TestContext(user, workspace);
    }

    private Wallet wallet(TestContext ctx, String name, String openingBalance) {
        return walletRepository.saveAndFlush(Wallet.builder()
                .workspace(ctx.workspace())
                .name(name)
                .walletType(WalletType.CASH)
                .openingBalance(new BigDecimal(openingBalance))
                .openingDate(LocalDate.of(2026, 7, 1))
                .includeInTotal(true)
                .isActive(true)
                .build());
    }

    private RecurringObligationTemplate template(TestContext ctx, ObligationDirection direction, String name) {
        return templateRepository.saveAndFlush(RecurringObligationTemplate.builder()
                .workspace(ctx.workspace())
                .name(name)
                .direction(direction)
                .amountMode(ObligationAmountMode.VARIABLE)
                .frequency(ObligationFrequency.MONTHLY)
                .intervalCount(1)
                .startDate(LocalDate.of(2026, 8, 1))
                .reminderDaysBefore(0)
                .createdByUser(ctx.user())
                .build());
    }

    private ObligationOccurrence occurrence(TestContext ctx, RecurringObligationTemplate template, String periodKey, LocalDate dueDate, String expectedAmount, ObligationOccurrenceStatus status, Transaction tx) {
        return occurrenceRepository.saveAndFlush(ObligationOccurrence.builder()
                .workspace(ctx.workspace())
                .template(template)
                .periodKey(periodKey)
                .dueDate(dueDate)
                .expectedAmount(expectedAmount == null ? null : new BigDecimal(expectedAmount))
                .status(status)
                .linkedTransaction(tx)
                .actualAmount(tx == null ? null : tx.getAmount())
                .completedAt(tx == null ? null : Instant.now())
                .build());
    }

    private Transaction transaction(TestContext ctx, Wallet wallet, String amount, LocalDate date) {
        return transactionRepository.saveAndFlush(Transaction.builder()
                .workspace(ctx.workspace())
                .createdByUser(ctx.user())
                .wallet(wallet)
                .transactionType(TransactionType.EXPENSE)
                .transactionStatus(TransactionStatus.POSTED)
                .amount(new BigDecimal(amount))
                .transactionDate(date)
                .walletUnknown(false)
                .affectsWalletBalance(true)
                .build());
    }

    private StudentLoan loan(TestContext ctx, String name, String minimum, String extra, StudentLoanStatus status) {
        return studentLoanRepository.saveAndFlush(StudentLoan.builder()
                .workspace(ctx.workspace())
                .name(name)
                .currentPrincipal(new BigDecimal("10000"))
                .annualInterestRate(new BigDecimal("0.052500"))
                .minimumMonthlyPayment(new BigDecimal(minimum))
                .plannedExtraMonthlyPayment(new BigDecimal(extra))
                .status(status)
                .createdByUser(ctx.user())
                .build());
    }

    private record TestContext(User user, Workspace workspace) {
    }
}
