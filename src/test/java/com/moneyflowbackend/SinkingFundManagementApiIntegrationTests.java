package com.moneyflowbackend;

import com.moneyflowbackend.auth.dto.LoginRequest;
import com.moneyflowbackend.auth.dto.RegisterRequest;
import com.moneyflowbackend.auth.dto.TokenResponse;
import com.moneyflowbackend.auth.dto.UserResponse;
import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.auth.service.AuthService;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.sinkingfund.dto.SinkingFundAllocationRequest;
import com.moneyflowbackend.sinkingfund.dto.SinkingFundAllocationResponse;
import com.moneyflowbackend.sinkingfund.dto.SinkingFundRequest;
import com.moneyflowbackend.sinkingfund.dto.SinkingFundResponse;
import com.moneyflowbackend.sinkingfund.dto.SinkingFundSummaryResponse;
import com.moneyflowbackend.sinkingfund.model.SinkingFundStatus;
import com.moneyflowbackend.sinkingfund.repository.SinkingFundRepository;
import com.moneyflowbackend.sinkingfund.service.SinkingFundService;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SinkingFundManagementApiIntegrationTests {
    @Autowired SinkingFundService sinkingFundService;
    @Autowired SinkingFundRepository sinkingFundRepository;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired AuthService authService;
    @Autowired MockMvc mockMvc;

    @Test
    void lifecycleAuthorizationAndWorkspaceIsolationWork() {
        TestContext owner = createContext("fund_lifecycle_owner", WorkspaceRole.OWNER);
        TestContext editor = createContext("fund_lifecycle_editor", WorkspaceRole.OWNER);
        TestContext viewer = createContext("fund_lifecycle_viewer", WorkspaceRole.OWNER);
        TestContext other = createContext("fund_lifecycle_other", WorkspaceRole.OWNER);
        workspaceMemberRepository.saveAndFlush(member(owner.workspace(), editor.user(), WorkspaceRole.EDITOR));
        workspaceMemberRepository.saveAndFlush(member(owner.workspace(), viewer.user(), WorkspaceRole.VIEWER));

        SinkingFundResponse fund = sinkingFundService.create(owner.workspace().getId(),
                request("  Insurance  ", "Annual", "1000000.00", "ACTIVE"), owner.user().getId());
        assertThat(fund.getName()).isEqualTo("Insurance");
        assertThat(fund.getWorkspaceId()).isEqualTo(owner.workspace().getId());
        assertThat(fund.getCreatedByUserId()).isEqualTo(owner.user().getId());

        SinkingFundResponse paused = sinkingFundService.update(owner.workspace().getId(), fund.getId(),
                request("Insurance", "Paused for now", "1000000.00", "PAUSED"), editor.user().getId());
        assertThat(paused.getStatus()).isEqualTo(SinkingFundStatus.PAUSED);
        assertThat(sinkingFundService.get(owner.workspace().getId(), fund.getId(), viewer.user().getId()).getId())
                .isEqualTo(fund.getId());
        assertThat(sinkingFundService.list(owner.workspace().getId(), SinkingFundStatus.PAUSED, 0, 10, viewer.user().getId())
                .getContent()).extracting(SinkingFundResponse::getId).contains(fund.getId());

        assertBusinessCode(() -> sinkingFundService.update(owner.workspace().getId(), fund.getId(),
                request("Viewer Edit", null, "1.00", "ACTIVE"), viewer.user().getId()), "FORBIDDEN");
        assertBusinessCode(() -> sinkingFundService.list(owner.workspace().getId(), null, 0, 10, other.user().getId()),
                "WORKSPACE_ACCESS_DENIED");
        assertBusinessCode(() -> sinkingFundService.get(other.workspace().getId(), fund.getId(), other.user().getId()),
                "SINKING_FUND_NOT_FOUND");

        SinkingFundResponse completed = sinkingFundService.update(owner.workspace().getId(), fund.getId(),
                request("Insurance", null, "1000000.00", "COMPLETED"), owner.user().getId());
        assertThat(completed.getStatus()).isEqualTo(SinkingFundStatus.COMPLETED);
        SinkingFundResponse archived = sinkingFundService.update(owner.workspace().getId(), fund.getId(),
                request("Insurance", null, "1000000.00", "ARCHIVED"), editor.user().getId());
        assertThat(archived.getStatus()).isEqualTo(SinkingFundStatus.ARCHIVED);
        assertThat(sinkingFundRepository.findById(fund.getId())).isPresent();
    }

    @Test
    void allocationLedgerIsAppendOnlyScopedAndPreventsNegativeReservedAmount() {
        TestContext owner = createContext("fund_alloc_owner", WorkspaceRole.OWNER);
        TestContext editor = createContext("fund_alloc_editor", WorkspaceRole.OWNER);
        TestContext viewer = createContext("fund_alloc_viewer", WorkspaceRole.OWNER);
        TestContext other = createContext("fund_alloc_other", WorkspaceRole.OWNER);
        workspaceMemberRepository.saveAndFlush(member(owner.workspace(), editor.user(), WorkspaceRole.EDITOR));
        workspaceMemberRepository.saveAndFlush(member(owner.workspace(), viewer.user(), WorkspaceRole.VIEWER));
        SinkingFundResponse fund = sinkingFundService.create(owner.workspace().getId(),
                request("Wi-Fi renewal", null, "400000.00", "ACTIVE"), owner.user().getId());

        SinkingFundAllocationResponse first = sinkingFundService.allocate(owner.workspace().getId(), fund.getId(),
                allocation("ALLOCATE", "200000.00", "Moved from cash plan"), owner.user().getId());
        SinkingFundAllocationResponse second = sinkingFundService.allocate(owner.workspace().getId(), fund.getId(),
                allocation("RELEASE", "50000.00", "Needed elsewhere"), editor.user().getId());
        SinkingFundAllocationResponse third = sinkingFundService.allocate(owner.workspace().getId(), fund.getId(),
                allocation("ADJUST", "-25000.00", "Bank statement correction"), owner.user().getId());

        assertThat(first.getAmountDelta()).isEqualByComparingTo("200000.00");
        assertThat(second.getAmountDelta()).isEqualByComparingTo("-50000.00");
        assertThat(third.getReservedAmount()).isEqualByComparingTo("125000.00");
        assertThat(third.getActorUserId()).isEqualTo(owner.user().getId());
        assertThat(sinkingFundService.history(owner.workspace().getId(), fund.getId(), 0, 10, viewer.user().getId())
                .getTotalElements()).isEqualTo(3);

        assertBusinessCode(() -> sinkingFundService.allocate(owner.workspace().getId(), fund.getId(),
                allocation("RELEASE", "200000.00", "Too much"), owner.user().getId()),
                "SINKING_FUND_RESERVED_AMOUNT_NEGATIVE");
        assertBusinessCode(() -> sinkingFundService.allocate(owner.workspace().getId(), fund.getId(),
                allocation("ADJUST", "1000.00", null), owner.user().getId()), "VALIDATION_ERROR");
        assertBusinessCode(() -> sinkingFundService.allocate(owner.workspace().getId(), fund.getId(),
                allocation("ALLOCATE", "1.00", "Viewer try"), viewer.user().getId()), "FORBIDDEN");
        assertBusinessCode(() -> sinkingFundService.allocate(other.workspace().getId(), fund.getId(),
                allocation("ALLOCATE", "1.00", "Other workspace"), other.user().getId()), "SINKING_FUND_NOT_FOUND");

        sinkingFundService.update(owner.workspace().getId(), fund.getId(),
                request("Wi-Fi renewal", null, "400000.00", "COMPLETED"), owner.user().getId());
        assertBusinessCode(() -> sinkingFundService.allocate(owner.workspace().getId(), fund.getId(),
                allocation("ALLOCATE", "1.00", "Completed"), owner.user().getId()), "SINKING_FUND_READ_ONLY");
    }

    @Test
    void summariesDeriveReservedRemainingProgressLatestAndActiveWorkspaceTotal() {
        TestContext owner = createContext("fund_summary_owner", WorkspaceRole.OWNER);
        SinkingFundResponse first = sinkingFundService.create(owner.workspace().getId(),
                request("Laptop", null, "400000.00", "ACTIVE"), owner.user().getId());
        SinkingFundResponse second = sinkingFundService.create(owner.workspace().getId(),
                request("Insurance", null, "200000.00", "ACTIVE"), owner.user().getId());
        sinkingFundService.allocate(owner.workspace().getId(), first.getId(),
                allocation("ALLOCATE", "100000.00", "Monthly reserve"), owner.user().getId());
        sinkingFundService.allocate(owner.workspace().getId(), second.getId(),
                allocation("ALLOCATE", "50000.00", "Monthly reserve"), owner.user().getId());
        sinkingFundService.update(owner.workspace().getId(), second.getId(),
                request("Insurance", null, "200000.00", "ARCHIVED"), owner.user().getId());

        SinkingFundSummaryResponse summary = sinkingFundService.summary(owner.workspace().getId(), first.getId(), owner.user().getId());
        assertThat(summary.getReservedAmount()).isEqualByComparingTo("100000.00");
        assertThat(summary.getRemainingAmount()).isEqualByComparingTo("300000.00");
        assertThat(summary.getProgressPercent()).isEqualByComparingTo("25.00");
        assertThat(summary.getLatestAllocation().getNote()).isEqualTo("Monthly reserve");
        assertThat(summary.getActiveWorkspaceReservedTotal()).isEqualByComparingTo("100000.00");

        assertThat(sinkingFundService.summaries(owner.workspace().getId(), SinkingFundStatus.ACTIVE, 0, 10, owner.user().getId())
                .getActiveWorkspaceReservedTotal()).isEqualByComparingTo("100000.00");
    }

    @Test
    void controllerRejectsInvalidJsonAndEnumsWithStableErrors() throws Exception {
        String username = "fund_http_" + UUID.randomUUID().toString().substring(0, 8);
        UserResponse registered = authService.register(registerRequest(username));
        TokenResponse token = authService.login(loginRequest(username));
        Workspace workspace = workspaceRepository.findAllByUserId(registered.getId()).getFirst();

        mockMvc.perform(post("/api/workspaces/" + workspace.getId() + "/sinking-funds")
                        .contentType("application/json")
                        .content("{")
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/workspaces/" + workspace.getId() + "/sinking-funds?status=DELETED")
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/workspaces/" + workspace.getId() + "/sinking-funds")
                        .contentType("application/json")
                        .content("""
                                {"name":"Laptop","targetAmount":400000,"status":"ACTIVE"}
                                """)
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Laptop"))
                .andExpect(jsonPath("$.data.workspace").doesNotExist());

        UUID fundId = sinkingFundRepository.findAllByWorkspaceIdAndStatus(
                workspace.getId(), SinkingFundStatus.ACTIVE, org.springframework.data.domain.PageRequest.of(0, 1))
                .getContent().getFirst().getId();

        mockMvc.perform(post("/api/workspaces/" + workspace.getId() + "/sinking-funds/" + fundId + "/allocations")
                        .contentType("application/json")
                        .content("""
                                {"type":"MOVE","amount":1000,"note":"bad enum"}
                                """)
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message", not(org.hamcrest.Matchers.containsString("constraint"))));
    }

    @Test
    void allocationsHaveNoTransactionOrWalletSideEffects() {
        TestContext owner = createContext("fund_side_effect_owner", WorkspaceRole.OWNER);
        SinkingFundResponse fund = sinkingFundService.create(owner.workspace().getId(),
                request("Side effect check", null, "100000.00", "ACTIVE"), owner.user().getId());
        long transactionsBefore = transactionRepository.count();
        long workspaceWalletsBefore = walletRepository.countByWorkspaceId(owner.workspace().getId());
        long walletsBefore = walletRepository.count();

        sinkingFundService.allocate(owner.workspace().getId(), fund.getId(),
                allocation("ALLOCATE", "50000.00", "Reserve only"), owner.user().getId());
        sinkingFundService.summary(owner.workspace().getId(), fund.getId(), owner.user().getId());
        sinkingFundService.history(owner.workspace().getId(), fund.getId(), 0, 10, owner.user().getId());

        assertThat(transactionRepository.count()).isEqualTo(transactionsBefore);
        assertThat(walletRepository.countByWorkspaceId(owner.workspace().getId())).isEqualTo(workspaceWalletsBefore);
        assertThat(walletRepository.count()).isEqualTo(walletsBefore);
    }

    private SinkingFundRequest request(String name, String description, String targetAmount, String status) {
        SinkingFundRequest request = new SinkingFundRequest();
        request.setName(name);
        request.setDescription(description);
        request.setTargetAmount(targetAmount == null ? null : new BigDecimal(targetAmount));
        request.setStatus(status);
        return request;
    }

    private SinkingFundAllocationRequest allocation(String type, String amount, String note) {
        SinkingFundAllocationRequest request = new SinkingFundAllocationRequest();
        request.setType(type);
        request.setAmount(new BigDecimal(amount));
        request.setNote(note);
        return request;
    }

    private TestContext createContext(String prefix, WorkspaceRole role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.saveAndFlush(User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .fullName("Sinking Fund API Test User")
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

    private RegisterRequest registerRequest(String username) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setEmail(username + "@example.com");
        request.setPassword("StrongPassword123");
        request.setFullName("Sinking Fund API User");
        return request;
    }

    private LoginRequest loginRequest(String username) {
        LoginRequest request = new LoginRequest();
        request.setIdentifier(username);
        request.setPassword("StrongPassword123");
        return request;
    }

    private void assertBusinessCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(code);
    }

    private record TestContext(User user, Workspace workspace) {
    }
}
