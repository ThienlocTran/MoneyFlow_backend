package com.moneyflowbackend;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.planning.dto.PlanningPreferenceRequest;
import com.moneyflowbackend.planning.dto.PlanningPreferenceResponse;
import com.moneyflowbackend.planning.model.PlanningHorizon;
import com.moneyflowbackend.planning.service.PlanningPreferenceService;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlanningPreferencesIntegrationTests {
    @Autowired PlanningPreferenceService preferenceService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired WalletRepository walletRepository;

    @Test
    void missingPreferenceReturnsDefaultsAndViewerCanRead() {
        TestContext owner = createContext("planning_pref_default", WorkspaceRole.OWNER);
        TestContext viewer = createContext("planning_pref_viewer", WorkspaceRole.OWNER);
        workspaceMemberRepository.saveAndFlush(WorkspaceMember.builder().workspace(owner.workspace()).user(viewer.user()).role(WorkspaceRole.VIEWER).build());

        PlanningPreferenceResponse response = preferenceService.get(owner.workspace().getId(), viewer.user().getId());

        assertThat(response.defaultHorizon()).isEqualTo(PlanningHorizon.CURRENT_MONTH);
        assertThat(response.useIncludedWallets()).isTrue();
        assertThat(response.selectedWalletIds()).isEmpty();
        assertThat(response.version()).isNull();
    }

    @Test
    void editorCanSaveCustomPreferencesWithWalletSelection() {
        TestContext owner = createContext("planning_pref_owner", WorkspaceRole.OWNER);
        TestContext editor = createContext("planning_pref_editor", WorkspaceRole.OWNER);
        workspaceMemberRepository.saveAndFlush(WorkspaceMember.builder().workspace(owner.workspace()).user(editor.user()).role(WorkspaceRole.EDITOR).build());
        Wallet wallet = wallet(owner, "Cash");

        PlanningPreferenceResponse saved = preferenceService.put(owner.workspace().getId(), new PlanningPreferenceRequest(
                "CUSTOM",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                false,
                List.of(wallet.getId()),
                null), editor.user().getId());

        assertThat(saved.defaultHorizon()).isEqualTo(PlanningHorizon.CUSTOM);
        assertThat(saved.customFrom()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(saved.useIncludedWallets()).isFalse();
        assertThat(saved.selectedWalletIds()).containsExactly(wallet.getId());
        assertThat(saved.version()).isNotNull();
        assertThat(preferenceService.get(owner.workspace().getId(), owner.user().getId()).selectedWalletIds()).containsExactly(wallet.getId());
    }

    @Test
    void viewerBadWalletBadCustomAndStaleVersionFail() {
        TestContext owner = createContext("planning_pref_guard", WorkspaceRole.OWNER);
        TestContext viewer = createContext("planning_pref_guard_viewer", WorkspaceRole.OWNER);
        TestContext other = createContext("planning_pref_guard_other", WorkspaceRole.OWNER);
        workspaceMemberRepository.saveAndFlush(WorkspaceMember.builder().workspace(owner.workspace()).user(viewer.user()).role(WorkspaceRole.VIEWER).build());
        Wallet otherWallet = wallet(other, "Other");

        assertThatThrownBy(() -> preferenceService.put(owner.workspace().getId(), new PlanningPreferenceRequest("CURRENT_MONTH", null, null, true, List.of(), null), viewer.user().getId()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> preferenceService.put(owner.workspace().getId(), new PlanningPreferenceRequest("CUSTOM", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 1), true, List.of(), null), owner.user().getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("to must be on or after from");
        assertThatThrownBy(() -> preferenceService.put(owner.workspace().getId(), new PlanningPreferenceRequest("CURRENT_MONTH", null, null, false, List.of(otherWallet.getId()), null), owner.user().getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("missing or inaccessible");

        PlanningPreferenceResponse saved = preferenceService.put(owner.workspace().getId(), new PlanningPreferenceRequest("CURRENT_MONTH", null, null, true, List.of(), null), owner.user().getId());
        assertThatThrownBy(() -> preferenceService.put(owner.workspace().getId(), new PlanningPreferenceRequest("CURRENT_MONTH", null, null, true, List.of(), saved.version() + 1), owner.user().getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("updated by another request");
    }

    private TestContext createContext(String prefix, WorkspaceRole role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .fullName("Planning Preference User")
                .build());
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .name(prefix + " workspace")
                .createdByUser(user)
                .build());
        workspaceMemberRepository.save(WorkspaceMember.builder().workspace(workspace).user(user).role(role).build());
        return new TestContext(user, workspace);
    }

    private Wallet wallet(TestContext ctx, String name) {
        return walletRepository.saveAndFlush(Wallet.builder()
                .workspace(ctx.workspace())
                .name(name)
                .walletType(WalletType.CASH)
                .openingBalance(new BigDecimal("100"))
                .isActive(true)
                .includeInTotal(true)
                .build());
    }

    private record TestContext(User user, Workspace workspace) {
    }
}
