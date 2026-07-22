package com.moneyflowbackend;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.emergencyfund.model.EmergencyFundBasisMode;
import com.moneyflowbackend.emergencyfund.model.EmergencyFundLedgerEntry;
import com.moneyflowbackend.emergencyfund.model.EmergencyFundLedgerType;
import com.moneyflowbackend.emergencyfund.model.EmergencyFundPlan;
import com.moneyflowbackend.emergencyfund.model.EmergencyFundPlanStatus;
import com.moneyflowbackend.emergencyfund.repository.EmergencyFundLedgerEntryRepository;
import com.moneyflowbackend.emergencyfund.repository.EmergencyFundPlanRepository;
import com.moneyflowbackend.planning.dto.ActuallySpendableResponse;
import com.moneyflowbackend.planning.service.PlanningService;
import com.moneyflowbackend.savingsgoal.model.SavingsGoal;
import com.moneyflowbackend.savingsgoal.model.SavingsGoalLedgerEntry;
import com.moneyflowbackend.savingsgoal.model.SavingsGoalLedgerType;
import com.moneyflowbackend.savingsgoal.model.SavingsGoalStatus;
import com.moneyflowbackend.savingsgoal.repository.SavingsGoalLedgerEntryRepository;
import com.moneyflowbackend.savingsgoal.repository.SavingsGoalRepository;
import com.moneyflowbackend.sinkingfund.model.SinkingFund;
import com.moneyflowbackend.sinkingfund.model.SinkingFundAllocation;
import com.moneyflowbackend.sinkingfund.model.SinkingFundAllocationType;
import com.moneyflowbackend.sinkingfund.model.SinkingFundStatus;
import com.moneyflowbackend.sinkingfund.repository.SinkingFundAllocationRepository;
import com.moneyflowbackend.sinkingfund.repository.SinkingFundRepository;
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
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlanningReserveIntegrationTests {
    @Autowired PlanningService planningService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired SinkingFundRepository sinkingFundRepository;
    @Autowired SinkingFundAllocationRepository sinkingFundAllocationRepository;
    @Autowired SavingsGoalRepository savingsGoalRepository;
    @Autowired SavingsGoalLedgerEntryRepository savingsGoalLedgerEntryRepository;
    @Autowired EmergencyFundPlanRepository emergencyFundPlanRepository;
    @Autowired EmergencyFundLedgerEntryRepository emergencyFundLedgerEntryRepository;
    @Autowired TransactionRepository transactionRepository;

    @Test
    void activeReservesReduceSpendableAndInactiveReserveStatusesAreExcluded() {
        TestContext ctx = createContext("planning_reserves");
        wallet(ctx, "Cash", "1000");
        sinking(ctx, SinkingFundStatus.ACTIVE, "100");
        sinking(ctx, SinkingFundStatus.PAUSED, "200");
        sinking(ctx, SinkingFundStatus.COMPLETED, "300");
        sinking(ctx, SinkingFundStatus.ARCHIVED, "400");
        savings(ctx, SavingsGoalStatus.ACTIVE, "50");
        savings(ctx, SavingsGoalStatus.PAUSED, "60");
        emergency(ctx, EmergencyFundPlanStatus.ACTIVE, "25");

        ActuallySpendableResponse response = planningService.actuallySpendable(ctx.workspace().getId(), ctx.user().getId(), null, null, null, null);

        assertThat(response.reserveBreakdown().sinkingFunds()).isEqualByComparingTo("100");
        assertThat(response.reserveBreakdown().savingsGoals()).isEqualByComparingTo("50");
        assertThat(response.reserveBreakdown().emergencyFund()).isEqualByComparingTo("25");
        assertThat(response.reserveBreakdown().total()).isEqualByComparingTo("175");
        assertThat(response.actuallySpendable()).isEqualByComparingTo("825");
        assertThat(response.assumptions()).anyMatch(text -> text.contains("PAUSED, COMPLETED, and ARCHIVED"));
        assertThat(response.warnings()).anyMatch(text -> text.contains("overlap"));
    }

    @Test
    void emptyReserveModulesAreZeroAndPlanningHasNoSideEffects() {
        TestContext ctx = createContext("planning_empty_reserves");
        wallet(ctx, "Cash", "1000");
        long walletCount = walletRepository.count();
        long transactionCount = transactionRepository.count();

        ActuallySpendableResponse response = planningService.actuallySpendable(ctx.workspace().getId(), ctx.user().getId(), null, null, null, null);

        assertThat(response.reserveBreakdown().total()).isEqualByComparingTo("0");
        assertThat(response.actuallySpendable()).isEqualByComparingTo("1000");
        assertThat(walletRepository.count()).isEqualTo(walletCount);
        assertThat(transactionRepository.count()).isEqualTo(transactionCount);
    }

    @Test
    void negativeReserveAggregateIsIncompleteAndDoesNotIncreaseSpendable() {
        TestContext ctx = createContext("planning_negative_reserve");
        wallet(ctx, "Cash", "1000");
        sinking(ctx, SinkingFundStatus.ACTIVE, "-50");

        ActuallySpendableResponse response = planningService.actuallySpendable(ctx.workspace().getId(), ctx.user().getId(), null, null, null, null);

        assertThat(response.reserveBreakdown().sinkingFunds()).isEqualByComparingTo("0");
        assertThat(response.actuallySpendable()).isEqualByComparingTo("1000");
        assertThat(response.incomplete()).isTrue();
        assertThat(response.warnings()).anyMatch(text -> text.contains("negative"));
    }

    private TestContext createContext(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .fullName("Planning Reserve User")
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

    private void sinking(TestContext ctx, SinkingFundStatus status, String amount) {
        SinkingFund fund = sinkingFundRepository.saveAndFlush(SinkingFund.builder()
                .workspace(ctx.workspace())
                .name(status.name() + UUID.randomUUID())
                .targetAmount(new BigDecimal("1000"))
                .status(status)
                .createdByUser(ctx.user())
                .build());
        sinkingFundAllocationRepository.saveAndFlush(SinkingFundAllocation.builder()
                .workspace(ctx.workspace())
                .sinkingFund(fund)
                .allocationType(new BigDecimal(amount).signum() < 0 ? SinkingFundAllocationType.RELEASE : SinkingFundAllocationType.ALLOCATE)
                .amountDelta(new BigDecimal(amount))
                .actorUser(ctx.user())
                .build());
    }

    private void savings(TestContext ctx, SavingsGoalStatus status, String amount) {
        SavingsGoal goal = savingsGoalRepository.saveAndFlush(SavingsGoal.builder()
                .workspace(ctx.workspace())
                .name(status.name() + UUID.randomUUID())
                .targetAmount(new BigDecimal("1000"))
                .status(status)
                .createdByUser(ctx.user())
                .build());
        savingsGoalLedgerEntryRepository.saveAndFlush(SavingsGoalLedgerEntry.builder()
                .workspace(ctx.workspace())
                .savingsGoal(goal)
                .entryType(SavingsGoalLedgerType.CONTRIBUTION)
                .amountDelta(new BigDecimal(amount))
                .actorUser(ctx.user())
                .build());
    }

    private void emergency(TestContext ctx, EmergencyFundPlanStatus status, String amount) {
        EmergencyFundPlan plan = emergencyFundPlanRepository.saveAndFlush(EmergencyFundPlan.builder()
                .workspace(ctx.workspace())
                .targetMonths(3)
                .basisMode(EmergencyFundBasisMode.MANUAL)
                .manualMonthlyExpense(new BigDecimal("1000"))
                .planStatus(status)
                .createdByUser(ctx.user())
                .build());
        emergencyFundLedgerEntryRepository.saveAndFlush(EmergencyFundLedgerEntry.builder()
                .workspace(ctx.workspace())
                .emergencyFundPlan(plan)
                .entryType(EmergencyFundLedgerType.ALLOCATE)
                .amountDelta(new BigDecimal(amount))
                .actorUser(ctx.user())
                .build());
    }

    private record TestContext(User user, Workspace workspace) {
    }
}
