package com.moneyflowbackend;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.emergencyfund.dto.EmergencyFundPlanRequest;
import com.moneyflowbackend.emergencyfund.dto.EmergencyFundPlanResponse;
import com.moneyflowbackend.emergencyfund.model.EmergencyFundBasisMode;
import com.moneyflowbackend.emergencyfund.model.EmergencyFundFundingStatus;
import com.moneyflowbackend.emergencyfund.model.EmergencyFundPlanStatus;
import com.moneyflowbackend.emergencyfund.repository.EmergencyFundLedgerEntryRepository;
import com.moneyflowbackend.emergencyfund.repository.EmergencyFundPlanRepository;
import com.moneyflowbackend.emergencyfund.service.EmergencyFundService;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EmergencyFundPlanApiIntegrationTests {
    @Autowired EmergencyFundService emergencyFundService;
    @Autowired EmergencyFundPlanRepository planRepository;
    @Autowired EmergencyFundLedgerEntryRepository ledgerRepository;
    @Autowired UserRepository userRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;

    @Test
    void ownerEditorWriteViewerReadOnlyAndOutsiderDenied() {
        TestContext owner = createContext("ef_auth_owner", WorkspaceRole.OWNER);
        TestContext editor = createContext("ef_auth_editor", WorkspaceRole.OWNER);
        TestContext viewer = createContext("ef_auth_viewer", WorkspaceRole.OWNER);
        TestContext outsider = createContext("ef_auth_outsider", WorkspaceRole.OWNER);
        workspaceMemberRepository.saveAndFlush(member(owner.workspace(), editor.user(), WorkspaceRole.EDITOR));
        workspaceMemberRepository.saveAndFlush(member(owner.workspace(), viewer.user(), WorkspaceRole.VIEWER));

        EmergencyFundPlanResponse created = emergencyFundService.put(owner.workspace().getId(), request(6, "1500.00"), owner.user().getId());
        EmergencyFundPlanResponse updated = emergencyFundService.put(owner.workspace().getId(), request(9, "1700.00"), editor.user().getId());

        assertThat(updated.getId()).isEqualTo(created.getId());
        assertThat(updated.getTargetMonths()).isEqualTo(9);
        assertThat(updated.getBasisMode()).isEqualTo(EmergencyFundBasisMode.MANUAL);
        assertThat(emergencyFundService.get(owner.workspace().getId(), viewer.user().getId()).getManualMonthlyExpense())
                .isEqualByComparingTo("1700.00");
        assertCode(() -> emergencyFundService.put(owner.workspace().getId(), request(3, "1000.00"), viewer.user().getId()), "FORBIDDEN");
        assertCode(() -> emergencyFundService.updateStatus(owner.workspace().getId(), EmergencyFundPlanStatus.PAUSED, viewer.user().getId()), "FORBIDDEN");
        assertCode(() -> emergencyFundService.get(owner.workspace().getId(), outsider.user().getId()), "WORKSPACE_ACCESS_DENIED");
        assertCode(() -> emergencyFundService.get(outsider.workspace().getId(), owner.user().getId()), "WORKSPACE_ACCESS_DENIED");
    }

    @Test
    void putMaintainsOnePlanPerWorkspaceAndStatusIsSeparate() {
        TestContext ctx = createContext("ef_single", WorkspaceRole.OWNER);

        EmergencyFundPlanResponse first = emergencyFundService.put(ctx.workspace().getId(), request(6, "1000.00"), ctx.user().getId());
        EmergencyFundPlanResponse second = emergencyFundService.put(ctx.workspace().getId(), request(12, "1100.00"), ctx.user().getId());
        EmergencyFundPlanResponse paused = emergencyFundService.updateStatus(ctx.workspace().getId(), EmergencyFundPlanStatus.PAUSED, ctx.user().getId());

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(planRepository.findAll()).filteredOn(plan -> plan.getWorkspace().getId().equals(ctx.workspace().getId())).hasSize(1);
        assertThat(paused.getPlanStatus()).isEqualTo(EmergencyFundPlanStatus.PAUSED);
        assertThat(emergencyFundService.updateStatus(ctx.workspace().getId(), EmergencyFundPlanStatus.ACTIVE, ctx.user().getId()).getPlanStatus())
                .isEqualTo(EmergencyFundPlanStatus.ACTIVE);
    }

    @Test
    void planValidationRejectsInvalidBoundaries() {
        TestContext ctx = createContext("ef_validation", WorkspaceRole.OWNER);

        assertCode(() -> emergencyFundService.get(ctx.workspace().getId(), ctx.user().getId()), "EMERGENCY_FUND_PLAN_NOT_FOUND");
        assertCode(() -> emergencyFundService.put(ctx.workspace().getId(), request(0, "1000.00"), ctx.user().getId()),
                "INVALID_EMERGENCY_FUND_TARGET_MONTHS");
        assertCode(() -> emergencyFundService.put(ctx.workspace().getId(), request(1, "0.00"), ctx.user().getId()),
                "INVALID_EMERGENCY_FUND_MANUAL_MONTHLY_EXPENSE");
        assertCode(() -> emergencyFundService.updateStatus(ctx.workspace().getId(), null, ctx.user().getId()),
                "INVALID_EMERGENCY_FUND_PLAN_STATUS");
    }

    @Test
    void ledgerRowsDriveReservedAmountAndDoNotAffectWalletsOrTransactions() {
        TestContext ctx = createContext("ef_ledger", WorkspaceRole.OWNER);
        long transactionCount = transactionRepository.count();
        long walletCount = walletRepository.count();

        EmergencyFundPlanResponse plan = emergencyFundService.put(ctx.workspace().getId(), request(6, "1000.00"), ctx.user().getId());
        emergencyFundService.allocate(ctx.workspace().getId(), ledger("400.00"), ctx.user().getId());
        emergencyFundService.release(ctx.workspace().getId(), ledger("125.00"), ctx.user().getId());

        EmergencyFundPlanResponse updated = emergencyFundService.get(ctx.workspace().getId(), ctx.user().getId());
        assertThat(updated.getReservedAmount()).isEqualByComparingTo("275.00");
        assertThat(updated.getEssentialMonthlyExpenseBasis()).isEqualByComparingTo("1000.00");
        assertThat(updated.getTargetAmount()).isEqualByComparingTo("6000.00");
        assertThat(updated.getFundingGap()).isEqualByComparingTo("5725.00");
        assertThat(updated.getCoverageMonths()).isEqualByComparingTo("0.28");
        assertThat(updated.getFundingStatus()).isEqualTo(EmergencyFundFundingStatus.UNDERFUNDED);
        assertThat(emergencyFundService.ledger(ctx.workspace().getId(), 0, 20, ctx.user().getId()).getContent()).hasSize(2);
        assertCode(() -> emergencyFundService.release(ctx.workspace().getId(), ledger("276.00"), ctx.user().getId()),
                "EMERGENCY_FUND_RESERVED_NEGATIVE");
        assertThat(ledgerRepository.sumReservedAmount(ctx.workspace().getId(), plan.getId())).isEqualByComparingTo("275.00");
        assertThat(transactionRepository.count()).isEqualTo(transactionCount);
        assertThat(walletRepository.count()).isEqualTo(walletCount);
    }

    @Test
    void fundingStatusUsesReservedLedgerAndDoesNotCollapsePlanStatus() {
        TestContext ctx = createContext("ef_coverage", WorkspaceRole.OWNER);
        EmergencyFundPlanResponse empty = emergencyFundService.put(ctx.workspace().getId(), request(2, "500.00"), ctx.user().getId());

        assertThat(empty.getFundingStatus()).isEqualTo(EmergencyFundFundingStatus.NOT_STARTED);
        assertThat(empty.getFundingGap()).isEqualByComparingTo("1000.00");
        emergencyFundService.allocate(ctx.workspace().getId(), ledger("1000.00"), ctx.user().getId());
        EmergencyFundPlanResponse funded = emergencyFundService.updateStatus(ctx.workspace().getId(), EmergencyFundPlanStatus.PAUSED, ctx.user().getId());

        assertThat(funded.getFundingStatus()).isEqualTo(EmergencyFundFundingStatus.FUNDED);
        assertThat(funded.getFundingGap()).isEqualByComparingTo("0.00");
        assertThat(funded.getCoverageMonths()).isEqualByComparingTo("2.00");
        assertThat(funded.getPlanStatus()).isEqualTo(EmergencyFundPlanStatus.PAUSED);
    }

    private EmergencyFundPlanRequest request(Integer targetMonths, String manualMonthlyExpense) {
        EmergencyFundPlanRequest request = new EmergencyFundPlanRequest();
        request.setTargetMonths(targetMonths);
        request.setBasisMode(EmergencyFundBasisMode.MANUAL);
        request.setManualMonthlyExpense(manualMonthlyExpense == null ? null : new BigDecimal(manualMonthlyExpense));
        return request;
    }

    private com.moneyflowbackend.emergencyfund.dto.EmergencyFundLedgerRequest ledger(String amount) {
        com.moneyflowbackend.emergencyfund.dto.EmergencyFundLedgerRequest request =
                new com.moneyflowbackend.emergencyfund.dto.EmergencyFundLedgerRequest();
        request.setAmount(new BigDecimal(amount));
        return request;
    }

    private TestContext createContext(String prefix, WorkspaceRole role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.saveAndFlush(User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .fullName("Emergency Fund Test User")
                .build());
        Workspace workspace = workspaceRepository.saveAndFlush(Workspace.builder()
                .name(prefix + " workspace")
                .createdByUser(user)
                .build());
        workspaceMemberRepository.saveAndFlush(member(workspace, user, role));
        return new TestContext(user, workspace);
    }

    private WorkspaceMember member(Workspace workspace, User user, WorkspaceRole role) {
        return WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(role)
                .build();
    }

    private void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(code);
    }

    private record TestContext(User user, Workspace workspace) {
    }
}
