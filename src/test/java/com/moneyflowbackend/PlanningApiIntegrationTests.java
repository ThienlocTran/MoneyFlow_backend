package com.moneyflowbackend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneyflowbackend.auth.dto.LoginRequest;
import com.moneyflowbackend.auth.dto.RegisterRequest;
import com.moneyflowbackend.auth.dto.TokenResponse;
import com.moneyflowbackend.auth.dto.UserResponse;
import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.auth.service.AuthService;
import com.moneyflowbackend.obligation.model.ObligationAmountMode;
import com.moneyflowbackend.obligation.model.ObligationDirection;
import com.moneyflowbackend.obligation.model.ObligationFrequency;
import com.moneyflowbackend.obligation.model.ObligationOccurrence;
import com.moneyflowbackend.obligation.model.RecurringObligationTemplate;
import com.moneyflowbackend.obligation.repository.ObligationOccurrenceRepository;
import com.moneyflowbackend.obligation.repository.RecurringObligationTemplateRepository;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PlanningApiIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired RecurringObligationTemplateRepository templateRepository;
    @Autowired ObligationOccurrenceRepository occurrenceRepository;
    @Autowired TransactionRepository transactionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void actuallySpendableEndpointAllowsViewerAndDoesNotMutateFinancialTables() throws Exception {
        AuthContext owner = auth("planning_api_owner");
        AuthContext viewer = auth("planning_api_viewer");
        Workspace workspace = workspaceRepository.findById(owner.workspaceId()).orElseThrow();
        User viewerUser = user(viewer.userId());
        workspaceMemberRepository.saveAndFlush(WorkspaceMember.builder().workspace(workspace).user(viewerUser).role(WorkspaceRole.VIEWER).build());
        wallet(workspace, user(owner.userId()), "Cash", "1000");
        obligation(workspace, user(owner.userId()), "Rent", "1500", LocalDate.of(2026, 8, 10));
        long walletCount = walletRepository.count();
        long transactionCount = transactionRepository.count();

        mockMvc.perform(get("/api/workspaces/" + owner.workspaceId() + "/planning/actually-spendable")
                        .header("Authorization", "Bearer " + viewer.accessToken())
                        .param("horizon", "CUSTOM")
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableLedger").value(1000))
                .andExpect(jsonPath("$.data.commitmentBreakdown.knownUpcomingObligations").value(1500))
                .andExpect(jsonPath("$.data.actuallySpendable").value(-500))
                .andExpect(jsonPath("$.data.incomplete").value(false));

        org.assertj.core.api.Assertions.assertThat(walletRepository.count()).isEqualTo(walletCount);
        org.assertj.core.api.Assertions.assertThat(transactionRepository.count()).isEqualTo(transactionCount);
    }

    @Test
    void preferencesEndpointAllowsOwnerEditorReadViewerAndBlocksViewerWrite() throws Exception {
        AuthContext owner = auth("planning_api_pref_owner");
        AuthContext editor = auth("planning_api_pref_editor");
        AuthContext viewer = auth("planning_api_pref_viewer");
        Workspace workspace = workspaceRepository.findById(owner.workspaceId()).orElseThrow();
        workspaceMemberRepository.saveAndFlush(WorkspaceMember.builder().workspace(workspace).user(user(editor.userId())).role(WorkspaceRole.EDITOR).build());
        workspaceMemberRepository.saveAndFlush(WorkspaceMember.builder().workspace(workspace).user(user(viewer.userId())).role(WorkspaceRole.VIEWER).build());
        Wallet wallet = wallet(workspace, user(owner.userId()), "Cash", "100");

        mockMvc.perform(put("/api/workspaces/" + owner.workspaceId() + "/planning/preferences")
                        .header("Authorization", "Bearer " + editor.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "defaultHorizon", "CUSTOM",
                                "customFrom", "2026-08-01",
                                "customTo", "2026-08-31",
                                "useIncludedWallets", false,
                                "selectedWalletIds", List.of(wallet.getId().toString())))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultHorizon").value("CUSTOM"))
                .andExpect(jsonPath("$.data.selectedWalletIds[0]").value(wallet.getId().toString()));

        mockMvc.perform(get("/api/workspaces/" + owner.workspaceId() + "/planning/preferences")
                        .header("Authorization", "Bearer " + viewer.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultHorizon").value("CUSTOM"));

        mockMvc.perform(put("/api/workspaces/" + owner.workspaceId() + "/planning/preferences")
                        .header("Authorization", "Bearer " + viewer.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("defaultHorizon", "CURRENT_MONTH"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidPlanningQueriesReturnBadRequest() throws Exception {
        AuthContext owner = auth("planning_api_invalid");

        mockMvc.perform(get("/api/workspaces/" + owner.workspaceId() + "/planning/actually-spendable")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .param("horizon", "NEXT_30_DAYS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PLANNING_HORIZON"));

        mockMvc.perform(get("/api/workspaces/" + owner.workspaceId() + "/planning/actually-spendable")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .param("horizon", "CUSTOM")
                        .param("from", "2026-08-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("requires from and to")));

        mockMvc.perform(get("/api/workspaces/" + owner.workspaceId() + "/planning/actually-spendable")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .param("horizon", "CUSTOM")
                        .param("from", "bad-date")
                        .param("to", "2026-08-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PLANNING_DATE"));
    }

    private AuthContext auth(String username) {
        RegisterRequest request = registerRequest(username);
        UserResponse user = authService.register(request);
        TokenResponse token = authService.login(loginRequest(request.getUsername()));
        Workspace workspace = workspaceRepository.findAllByUserId(user.getId()).get(0);
        return new AuthContext(user.getId(), token.getAccessToken(), workspace.getId());
    }

    private RegisterRequest registerRequest(String username) {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(username + "_" + UUID.randomUUID().toString().substring(0, 8));
        req.setEmail(req.getUsername() + "@example.com");
        req.setPassword("StrongPassword123");
        req.setFullName("Planning API Test");
        return req;
    }

    private LoginRequest loginRequest(String username) {
        LoginRequest req = new LoginRequest();
        req.setIdentifier(username);
        req.setPassword("StrongPassword123");
        return req;
    }

    private User user(UUID id) {
        return userRepository.findById(id).orElseThrow();
    }

    private Wallet wallet(Workspace workspace, User user, String name, String openingBalance) {
        return walletRepository.saveAndFlush(Wallet.builder()
                .workspace(workspace)
                .name(name)
                .walletType(WalletType.CASH)
                .openingBalance(new BigDecimal(openingBalance))
                .openingDate(LocalDate.of(2026, 7, 1))
                .isActive(true)
                .includeInTotal(true)
                .build());
    }

    private void obligation(Workspace workspace, User user, String name, String amount, LocalDate dueDate) {
        RecurringObligationTemplate template = templateRepository.saveAndFlush(RecurringObligationTemplate.builder()
                .workspace(workspace)
                .name(name)
                .direction(ObligationDirection.PAYABLE)
                .amountMode(ObligationAmountMode.FIXED)
                .defaultAmount(new BigDecimal(amount))
                .frequency(ObligationFrequency.MONTHLY)
                .intervalCount(1)
                .startDate(dueDate)
                .reminderDaysBefore(0)
                .createdByUser(user)
                .build());
        occurrenceRepository.saveAndFlush(ObligationOccurrence.builder()
                .workspace(workspace)
                .template(template)
                .periodKey(dueDate.toString())
                .dueDate(dueDate)
                .expectedAmount(new BigDecimal(amount))
                .build());
    }

    private record AuthContext(UUID userId, String accessToken, UUID workspaceId) {
    }
}
