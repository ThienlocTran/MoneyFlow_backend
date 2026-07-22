package com.moneyflowbackend;

import com.moneyflowbackend.auth.dto.LoginRequest;
import com.moneyflowbackend.auth.dto.RegisterRequest;
import com.moneyflowbackend.auth.dto.TokenResponse;
import com.moneyflowbackend.auth.dto.UserResponse;
import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.auth.service.AuthService;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.income.dto.IncomeSourceRequest;
import com.moneyflowbackend.income.dto.IncomeSourceResponse;
import com.moneyflowbackend.income.model.IncomeSource;
import com.moneyflowbackend.income.model.IncomeSourceStatus;
import com.moneyflowbackend.income.model.IncomeSourceType;
import com.moneyflowbackend.income.repository.IncomeSourceRepository;
import com.moneyflowbackend.income.service.IncomeSourceService;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IncomeSourceManagementApiIntegrationTests {
    @Autowired IncomeSourceService incomeSourceService;
    @Autowired IncomeSourceRepository incomeSourceRepository;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired AuthService authService;
    @Autowired MockMvc mockMvc;

    @Test
    void readListSearchAndAuthorizationRulesWork() {
        TestContext owner = createContext("income_read_owner", WorkspaceRole.OWNER);
        TestContext editor = createContext("income_read_editor", WorkspaceRole.OWNER);
        TestContext viewer = createContext("income_read_viewer", WorkspaceRole.OWNER);
        TestContext outsider = createContext("income_read_outsider", WorkspaceRole.OWNER);
        workspaceMemberRepository.saveAndFlush(member(owner.workspace(), editor.user(), WorkspaceRole.EDITOR));
        workspaceMemberRepository.saveAndFlush(member(owner.workspace(), viewer.user(), WorkspaceRole.VIEWER));

        IncomeSourceResponse salary = incomeSourceService.create(owner.workspace().getId(),
                request("Salary ABC", "SALARY", "Main job"), owner.user().getId());
        IncomeSourceResponse freelance = incomeSourceService.create(owner.workspace().getId(),
                request("freelance Design", "FREELANCE", null), editor.user().getId());
        IncomeSourceResponse rental = incomeSourceService.create(owner.workspace().getId(),
                request("Room Rental", "RENTAL", null), owner.user().getId());
        incomeSourceService.archive(owner.workspace().getId(), rental.getId(), owner.user().getId());

        assertThat(incomeSourceService.list(owner.workspace().getId(), null, null, owner.user().getId()))
                .extracting(IncomeSourceResponse::getId)
                .containsExactly(freelance.getId(), salary.getId());
        assertThat(incomeSourceService.list(owner.workspace().getId(), IncomeSourceStatus.ACTIVE, "  SALARY  ", editor.user().getId()))
                .extracting(IncomeSourceResponse::getId)
                .containsExactly(salary.getId());
        assertThat(incomeSourceService.list(owner.workspace().getId(), IncomeSourceStatus.ACTIVE, "   ", viewer.user().getId()))
                .hasSize(2);
        assertThat(incomeSourceService.list(owner.workspace().getId(), IncomeSourceStatus.ARCHIVED, null, viewer.user().getId()))
                .extracting(IncomeSourceResponse::getId)
                .containsExactly(rental.getId());
        assertThat(incomeSourceService.get(owner.workspace().getId(), rental.getId(), viewer.user().getId()).getStatus())
                .isEqualTo(IncomeSourceStatus.ARCHIVED);
        assertBusinessCode(() -> incomeSourceService.list(owner.workspace().getId(), null, null, outsider.user().getId()),
                "WORKSPACE_ACCESS_DENIED");
        assertBusinessCode(() -> incomeSourceService.get(outsider.workspace().getId(), salary.getId(), outsider.user().getId()),
                "INCOME_SOURCE_NOT_FOUND");
    }

    @Test
    void createValidationDuplicateAndWorkspaceRulesWork() {
        TestContext owner = createContext("income_create_owner", WorkspaceRole.OWNER);
        TestContext editor = createContext("income_create_editor", WorkspaceRole.OWNER);
        TestContext viewer = createContext("income_create_viewer", WorkspaceRole.OWNER);
        TestContext other = createContext("income_create_other", WorkspaceRole.OWNER);
        TestContext outsider = createContext("income_create_outsider", WorkspaceRole.OWNER);
        workspaceMemberRepository.saveAndFlush(member(owner.workspace(), editor.user(), WorkspaceRole.EDITOR));
        workspaceMemberRepository.saveAndFlush(member(owner.workspace(), viewer.user(), WorkspaceRole.VIEWER));

        IncomeSourceResponse ownerCreated = incomeSourceService.create(owner.workspace().getId(),
                request("  Salary ABC  ", null, "   "), owner.user().getId());
        IncomeSourceResponse editorCreated = incomeSourceService.create(owner.workspace().getId(),
                request("Be Driver", "GIG_PLATFORM", "Night rides"), editor.user().getId());

        assertThat(ownerCreated.getName()).isEqualTo("Salary ABC");
        assertThat(ownerCreated.getType()).isNull();
        assertThat(ownerCreated.getDescription()).isNull();
        assertThat(ownerCreated.getWorkspaceId()).isEqualTo(owner.workspace().getId());
        assertThat(ownerCreated.getCreatedByUserId()).isEqualTo(owner.user().getId());
        assertThat(ownerCreated.getVersion()).isNotNull();
        assertThat(editorCreated.getType()).isEqualTo(IncomeSourceType.GIG_PLATFORM);

        assertBusinessCode(() -> incomeSourceService.create(owner.workspace().getId(), request(" ", "OTHER", null), owner.user().getId()),
                "VALIDATION_ERROR");
        assertBusinessCode(() -> incomeSourceService.create(owner.workspace().getId(), request("Bad", "NOT_A_TYPE", null), owner.user().getId()),
                "INVALID_INCOME_SOURCE_TYPE");
        assertBusinessCode(() -> incomeSourceService.create(owner.workspace().getId(), request("salary abc", "SALARY", null), owner.user().getId()),
                "INCOME_SOURCE_NAME_ALREADY_EXISTS");
        assertBusinessCode(() -> incomeSourceService.create(owner.workspace().getId(), request("Viewer Source", "OTHER", null), viewer.user().getId()),
                "FORBIDDEN");
        assertBusinessCode(() -> incomeSourceService.create(owner.workspace().getId(), request("Outsider Source", "OTHER", null), outsider.user().getId()),
                "WORKSPACE_ACCESS_DENIED");

        assertThat(incomeSourceService.create(other.workspace().getId(), request("SALARY ABC", "SALARY", null), other.user().getId()).getName())
                .isEqualTo("SALARY ABC");
        incomeSourceService.archive(owner.workspace().getId(), ownerCreated.getId(), owner.user().getId());
        assertThat(incomeSourceService.create(owner.workspace().getId(), request(" salary abc ", "OTHER", null), owner.user().getId()).getName())
                .isEqualTo("salary abc");
    }

    @Test
    void updateArchiveRestoreAndCollisionRulesWork() {
        TestContext owner = createContext("income_lifecycle_owner", WorkspaceRole.OWNER);
        TestContext editor = createContext("income_lifecycle_editor", WorkspaceRole.OWNER);
        TestContext viewer = createContext("income_lifecycle_viewer", WorkspaceRole.OWNER);
        TestContext other = createContext("income_lifecycle_other", WorkspaceRole.OWNER);
        workspaceMemberRepository.saveAndFlush(member(owner.workspace(), editor.user(), WorkspaceRole.EDITOR));
        workspaceMemberRepository.saveAndFlush(member(owner.workspace(), viewer.user(), WorkspaceRole.VIEWER));
        IncomeSourceResponse salary = incomeSourceService.create(owner.workspace().getId(),
                request("Salary", "SALARY", "Old"), owner.user().getId());
        IncomeSourceResponse shop = incomeSourceService.create(owner.workspace().getId(),
                request("Shop", "BUSINESS", "Store"), owner.user().getId());

        IncomeSourceResponse updated = incomeSourceService.update(owner.workspace().getId(), salary.getId(),
                request("Main Salary", null, ""), editor.user().getId());
        assertThat(updated.getName()).isEqualTo("Main Salary");
        assertThat(updated.getType()).isNull();
        assertThat(updated.getDescription()).isNull();
        assertBusinessCode(() -> incomeSourceService.update(owner.workspace().getId(), salary.getId(),
                request("SHOP", "OTHER", null), owner.user().getId()), "INCOME_SOURCE_NAME_ALREADY_EXISTS");
        assertBusinessCode(() -> incomeSourceService.update(owner.workspace().getId(), salary.getId(),
                request("Viewer Edit", "OTHER", null), viewer.user().getId()), "FORBIDDEN");
        assertBusinessCode(() -> incomeSourceService.update(other.workspace().getId(), salary.getId(),
                request("Other Edit", "OTHER", null), other.user().getId()), "INCOME_SOURCE_NOT_FOUND");

        assertThat(incomeSourceService.archive(owner.workspace().getId(), salary.getId(), owner.user().getId()).getStatus())
                .isEqualTo(IncomeSourceStatus.ARCHIVED);
        assertThat(incomeSourceService.archive(owner.workspace().getId(), salary.getId(), owner.user().getId()).getStatus())
                .isEqualTo(IncomeSourceStatus.ARCHIVED);
        assertBusinessCode(() -> incomeSourceService.update(owner.workspace().getId(), salary.getId(),
                request("Archived Edit", "OTHER", null), owner.user().getId()), "INCOME_SOURCE_ARCHIVED");
        assertThat(incomeSourceService.restore(owner.workspace().getId(), salary.getId(), editor.user().getId()).getStatus())
                .isEqualTo(IncomeSourceStatus.ACTIVE);
        assertThat(incomeSourceService.restore(owner.workspace().getId(), salary.getId(), owner.user().getId()).getStatus())
                .isEqualTo(IncomeSourceStatus.ACTIVE);

        incomeSourceService.archive(owner.workspace().getId(), salary.getId(), owner.user().getId());
        incomeSourceService.update(owner.workspace().getId(), shop.getId(), request("Main Salary", "BUSINESS", "Store"), owner.user().getId());
        assertBusinessCode(() -> incomeSourceService.restore(owner.workspace().getId(), salary.getId(), owner.user().getId()),
                "INCOME_SOURCE_NAME_ALREADY_EXISTS");
        assertBusinessCode(() -> incomeSourceService.archive(other.workspace().getId(), shop.getId(), other.user().getId()),
                "INCOME_SOURCE_NOT_FOUND");
        assertBusinessCode(() -> incomeSourceService.restore(other.workspace().getId(), shop.getId(), other.user().getId()),
                "INCOME_SOURCE_NOT_FOUND");

        IncomeSource archived = incomeSourceRepository.findById(salary.getId()).orElseThrow();
        assertThat(archived.getDescription()).isNull();
        assertThat(archived.getStatus()).isEqualTo(IncomeSourceStatus.ARCHIVED);
    }

    @Test
    void controllerReturnsWrappedJsonStableErrorsAndNoEntityInternals() throws Exception {
        String username = "income_http_" + UUID.randomUUID().toString().substring(0, 8);
        UserResponse registered = authService.register(registerRequest(username));
        TokenResponse token = authService.login(loginRequest(username));
        Workspace workspace = workspaceRepository.findAllByUserId(registered.getId()).getFirst();

        mockMvc.perform(post("/api/workspaces/" + workspace.getId() + "/income-sources")
                        .contentType("application/json")
                        .content("""
                                {"name":"Salary","type":"SALARY","description":"Main job"}
                                """)
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Salary"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.workspace").doesNotExist())
                .andExpect(jsonPath("$.data.createdByUser").doesNotExist())
                .andExpect(jsonPath("$.data.hibernateLazyInitializer").doesNotExist());

        UUID sourceId = incomeSourceRepository.findAllByWorkspaceIdAndStatusOrderByNameAsc(
                workspace.getId(), IncomeSourceStatus.ACTIVE).getFirst().getId();

        mockMvc.perform(get("/api/workspaces/" + workspace.getId() + "/income-sources?search=salary")
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(sourceId.toString()));
        mockMvc.perform(get("/api/workspaces/" + workspace.getId() + "/income-sources?status=&search=")
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(sourceId.toString()));

        mockMvc.perform(put("/api/workspaces/" + workspace.getId() + "/income-sources/" + sourceId)
                        .contentType("application/json")
                        .content("""
                                {"name":"Salary 2","type":"NOT_REAL"}
                                """)
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INCOME_SOURCE_TYPE"))
                .andExpect(jsonPath("$.message", not(org.hamcrest.Matchers.containsString("constraint"))))
                .andExpect(jsonPath("$.message", not(org.hamcrest.Matchers.containsString("SQL"))));

        mockMvc.perform(post("/api/workspaces/" + workspace.getId() + "/income-sources/" + sourceId + "/archive")
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
        mockMvc.perform(post("/api/workspaces/" + workspace.getId() + "/income-sources/" + sourceId + "/restore")
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(post("/api/workspaces/" + workspace.getId() + "/income-sources")
                        .contentType("application/json")
                        .content("""
                                {"name":" salary ","type":"OTHER"}
                                """)
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INCOME_SOURCE_NAME_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message", not(org.hamcrest.Matchers.containsString("uq_income_sources"))));
    }

    private IncomeSourceRequest request(String name, String type, String description) {
        IncomeSourceRequest request = new IncomeSourceRequest();
        request.setName(name);
        request.setType(type);
        request.setDescription(description);
        return request;
    }

    private TestContext createContext(String prefix, WorkspaceRole role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.saveAndFlush(User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .fullName("Income Source Test User")
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
        request.setFullName("Income API User");
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
