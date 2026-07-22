package com.moneyflowbackend;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.savingsgoal.dto.SavingsGoalLedgerRequest;
import com.moneyflowbackend.savingsgoal.dto.SavingsGoalRequest;
import com.moneyflowbackend.savingsgoal.dto.SavingsGoalResponse;
import com.moneyflowbackend.savingsgoal.model.SavingsGoalStatus;
import com.moneyflowbackend.savingsgoal.repository.SavingsGoalLedgerEntryRepository;
import com.moneyflowbackend.savingsgoal.service.SavingsGoalService;
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
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SavingsGoalIntegrationTests {
    @Autowired SavingsGoalService goalService;
    @Autowired SavingsGoalLedgerEntryRepository ledgerRepository;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired WalletRepository walletRepository;

    @Test
    void ownerEditorWriteViewerReadOnlyAndOutsiderDenied() {
        TestContext owner = createContext("goal_auth_owner", WorkspaceRole.OWNER);
        TestContext editor = createContext("goal_auth_editor", WorkspaceRole.OWNER);
        TestContext viewer = createContext("goal_auth_viewer", WorkspaceRole.OWNER);
        TestContext outsider = createContext("goal_auth_outsider", WorkspaceRole.OWNER);
        workspaceMemberRepository.saveAndFlush(member(owner.workspace(), editor.user(), WorkspaceRole.EDITOR));
        workspaceMemberRepository.saveAndFlush(member(owner.workspace(), viewer.user(), WorkspaceRole.VIEWER));

        SavingsGoalResponse goal = goalService.create(owner.workspace().getId(),
                request("Laptop", "Work machine", "30000000.00", LocalDate.of(2027, 1, 10)), owner.user().getId());
        assertThat(goalService.update(owner.workspace().getId(), goal.getId(),
                request("Laptop Pro", "", "32000000.00", null), editor.user().getId()).getName())
                .isEqualTo("Laptop Pro");
        assertThat(goalService.get(owner.workspace().getId(), goal.getId(), viewer.user().getId()).getTargetDate()).isNull();
        assertThat(goalService.list(owner.workspace().getId(), null, "laptop", false, 0, 20, viewer.user().getId())
                .getContent()).hasSize(1);

        assertCode(() -> goalService.create(owner.workspace().getId(), request("Bad", null, "1.00", null), viewer.user().getId()), "FORBIDDEN");
        assertCode(() -> goalService.updateStatus(owner.workspace().getId(), goal.getId(), SavingsGoalStatus.PAUSED, viewer.user().getId()), "FORBIDDEN");
        assertCode(() -> goalService.contribute(owner.workspace().getId(), goal.getId(), ledger("1.00"), viewer.user().getId()), "FORBIDDEN");
        assertCode(() -> goalService.list(owner.workspace().getId(), null, null, false, 0, 20, outsider.user().getId()), "WORKSPACE_ACCESS_DENIED");
        assertCode(() -> goalService.get(outsider.workspace().getId(), goal.getId(), outsider.user().getId()), "SAVINGS_GOAL_NOT_FOUND");
    }

    @Test
    void ledgerRowsDriveReservedAmountAndDoNotAffectWalletsOrTransactions() {
        TestContext ctx = createContext("goal_ledger", WorkspaceRole.OWNER);
        long transactionCount = transactionRepository.count();
        long walletCount = walletRepository.count();

        SavingsGoalResponse goal = goalService.create(ctx.workspace().getId(),
                request("Camera", null, "1000.00", LocalDate.of(2027, 5, 1)), ctx.user().getId());
        assertThat(goal.getReservedAmount()).isEqualByComparingTo("0.00");

        goalService.contribute(ctx.workspace().getId(), goal.getId(), ledger("300.00"), ctx.user().getId());
        goalService.release(ctx.workspace().getId(), goal.getId(), ledger("125.00"), ctx.user().getId());

        SavingsGoalResponse updated = goalService.get(ctx.workspace().getId(), goal.getId(), ctx.user().getId());
        assertThat(updated.getReservedAmount()).isEqualByComparingTo("175.00");
        assertThat(updated.getRemainingAmount()).isEqualByComparingTo("825.00");
        assertThat(updated.getProgressPercent()).isEqualByComparingTo("17.50");
        assertThat(goalService.summary(ctx.workspace().getId(), goal.getId(), ctx.user().getId()).getTargetDate())
                .isEqualTo(LocalDate.of(2027, 5, 1));
        assertThat(goalService.workspaceSummary(ctx.workspace().getId(), false, ctx.user().getId()).getTotalReservedAmount())
                .isEqualByComparingTo("175.00");
        assertThat(goalService.ledger(ctx.workspace().getId(), goal.getId(), 0, 20, ctx.user().getId()).getContent())
                .hasSize(2);

        assertCode(() -> goalService.release(ctx.workspace().getId(), goal.getId(), ledger("176.00"), ctx.user().getId()),
                "SAVINGS_GOAL_RESERVED_NEGATIVE");
        assertThat(ledgerRepository.sumReservedAmount(ctx.workspace().getId(), goal.getId())).isEqualByComparingTo("175.00");
        assertThat(transactionRepository.count()).isEqualTo(transactionCount);
        assertThat(walletRepository.count()).isEqualTo(walletCount);
    }

    @Test
    void validationArchiveDuplicateAndBoundaryRulesWork() {
        TestContext ctx = createContext("goal_rules", WorkspaceRole.OWNER);
        SavingsGoalResponse trip = goalService.create(ctx.workspace().getId(),
                request("Trip", "  ", "5000.00", null), ctx.user().getId());
        assertThat(trip.getDescription()).isNull();

        assertCode(() -> goalService.create(ctx.workspace().getId(), request(" ", null, "1.00", null), ctx.user().getId()),
                "INVALID_SAVINGS_GOAL_NAME");
        assertCode(() -> goalService.create(ctx.workspace().getId(), request("Zero", null, "0.00", null), ctx.user().getId()),
                "INVALID_SAVINGS_GOAL_TARGET");
        assertCode(() -> goalService.create(ctx.workspace().getId(), request(" trip ", null, "1.00", null), ctx.user().getId()),
                "SAVINGS_GOAL_NAME_ALREADY_EXISTS");

        assertThat(goalService.updateStatus(ctx.workspace().getId(), trip.getId(), SavingsGoalStatus.COMPLETED, ctx.user().getId()).getStatus())
                .isEqualTo(SavingsGoalStatus.COMPLETED);
        assertThat(goalService.archive(ctx.workspace().getId(), trip.getId(), ctx.user().getId()).getStatus())
                .isEqualTo(SavingsGoalStatus.ARCHIVED);
        assertCode(() -> goalService.update(ctx.workspace().getId(), trip.getId(),
                request("Trip 2", null, "100.00", null), ctx.user().getId()), "SAVINGS_GOAL_ARCHIVED");
        assertCode(() -> goalService.contribute(ctx.workspace().getId(), trip.getId(), ledger("10.00"), ctx.user().getId()),
                "SAVINGS_GOAL_ARCHIVED");

        SavingsGoalResponse reused = goalService.create(ctx.workspace().getId(),
                request(" trip ", null, "100.00", null), ctx.user().getId());
        assertThat(reused.getName()).isEqualTo("trip");
        assertThat(goalService.list(ctx.workspace().getId(), null, null, false, 0, 20, ctx.user().getId()).getContent())
                .extracting(SavingsGoalResponse::getId)
                .containsExactly(reused.getId());
        assertThat(goalService.list(ctx.workspace().getId(), SavingsGoalStatus.ARCHIVED, null, true, 0, 20, ctx.user().getId()).getContent())
                .extracting(SavingsGoalResponse::getId)
                .containsExactly(trip.getId());
    }

    private SavingsGoalRequest request(String name, String description, String targetAmount, LocalDate targetDate) {
        SavingsGoalRequest request = new SavingsGoalRequest();
        request.setName(name);
        request.setDescription(description);
        request.setTargetAmount(targetAmount == null ? null : new BigDecimal(targetAmount));
        request.setTargetDate(targetDate);
        return request;
    }

    private SavingsGoalLedgerRequest ledger(String amount) {
        SavingsGoalLedgerRequest request = new SavingsGoalLedgerRequest();
        request.setAmount(new BigDecimal(amount));
        return request;
    }

    private TestContext createContext(String prefix, WorkspaceRole role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.saveAndFlush(User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .fullName("Savings Goal Test User")
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
