package com.moneyflowbackend;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.emergencyfund.model.EmergencyFundBasisMode;
import com.moneyflowbackend.emergencyfund.model.EmergencyFundPlan;
import com.moneyflowbackend.emergencyfund.model.EmergencyFundPlanStatus;
import com.moneyflowbackend.emergencyfund.repository.EmergencyFundPlanRepository;
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

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EmergencyFundPersistenceIntegrationTests {
    @Autowired EmergencyFundPlanRepository planRepository;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;

    @Test
    void planPersistsAsOneManualPlanPerWorkspace() {
        TestContext ctx = createContext("ef_plan");

        EmergencyFundPlan plan = planRepository.saveAndFlush(EmergencyFundPlan.builder()
                .workspace(ctx.workspace())
                .targetMonths(6)
                .basisMode(EmergencyFundBasisMode.MANUAL)
                .manualMonthlyExpense(new BigDecimal("1200.00"))
                .planStatus(EmergencyFundPlanStatus.ACTIVE)
                .createdByUser(ctx.user())
                .build());

        assertThat(planRepository.findByWorkspaceId(ctx.workspace().getId())).contains(plan);
        assertThat(plan.getVersion()).isNotNull();
        assertThat(plan.getCreatedAt()).isNotNull();
        assertThat(plan.getUpdatedAt()).isNotNull();
    }

    private TestContext createContext(String prefix) {
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
        workspaceMemberRepository.saveAndFlush(WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(WorkspaceRole.OWNER)
                .build());
        return new TestContext(user, workspace);
    }

    private record TestContext(User user, Workspace workspace) {
    }
}
