package com.moneyflowbackend;

import com.moneyflowbackend.auth.dto.LoginRequest;
import com.moneyflowbackend.auth.dto.RegisterRequest;
import com.moneyflowbackend.auth.dto.TokenResponse;
import com.moneyflowbackend.auth.dto.UserResponse;
import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.auth.service.AuthService;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.studentloan.dto.StudentLoanRequest;
import com.moneyflowbackend.studentloan.dto.StudentLoanResponse;
import com.moneyflowbackend.studentloan.model.StudentLoan;
import com.moneyflowbackend.studentloan.model.StudentLoanStatus;
import com.moneyflowbackend.studentloan.repository.StudentLoanRepository;
import com.moneyflowbackend.studentloan.service.StudentLoanService;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StudentLoanManagementApiIntegrationTests {
    @Autowired StudentLoanService studentLoanService;
    @Autowired StudentLoanRepository studentLoanRepository;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired AuthService authService;
    @Autowired MockMvc mockMvc;

    @Test
    void serviceLifecycleAndWorkspaceAuthorizationWork() {
        TestContext owner = createContext("loan_owner", WorkspaceRole.OWNER);
        TestContext editor = createContext("loan_editor", WorkspaceRole.OWNER);
        TestContext viewer = createContext("loan_viewer", WorkspaceRole.OWNER);
        TestContext outsider = createContext("loan_outsider", WorkspaceRole.OWNER);
        workspaceMemberRepository.saveAndFlush(member(owner.workspace(), editor.user(), WorkspaceRole.EDITOR));
        workspaceMemberRepository.saveAndFlush(member(owner.workspace(), viewer.user(), WorkspaceRole.VIEWER));

        StudentLoanResponse created = studentLoanService.create(owner.workspace().getId(), request("Federal Direct", "Servicer"), owner.user().getId());
        assertThat(created.getStatus()).isEqualTo(StudentLoanStatus.ACTIVE);
        assertThat(created.getCurrentPrincipal()).isEqualByComparingTo("12000");

        StudentLoanResponse updated = studentLoanService.update(owner.workspace().getId(), created.getId(),
                request("Federal Direct Updated", "Servicer B"), editor.user().getId());
        assertThat(updated.getName()).isEqualTo("Federal Direct Updated");
        assertThat(studentLoanService.list(owner.workspace().getId(), null, 0, 20, owner.user().getId()).getItems())
                .extracting(StudentLoanResponse::getId)
                .containsExactly(created.getId());
        assertThat(studentLoanService.transitionStatus(owner.workspace().getId(), created.getId(), StudentLoanStatus.PAUSED, owner.user().getId()).getStatus())
                .isEqualTo(StudentLoanStatus.PAUSED);
        assertThat(studentLoanService.transitionStatus(owner.workspace().getId(), created.getId(), StudentLoanStatus.ACTIVE, owner.user().getId()).getStatus())
                .isEqualTo(StudentLoanStatus.ACTIVE);
        assertThat(studentLoanService.markPaidOff(owner.workspace().getId(), created.getId(), owner.user().getId()).getStatus())
                .isEqualTo(StudentLoanStatus.PAID_OFF);
        assertThat(studentLoanService.archive(owner.workspace().getId(), created.getId(), owner.user().getId()).getStatus())
                .isEqualTo(StudentLoanStatus.ARCHIVED);

        assertBusinessCode(() -> studentLoanService.create(owner.workspace().getId(), request("Viewer", "Servicer"), viewer.user().getId()), "FORBIDDEN");
        assertBusinessCode(() -> studentLoanService.create(owner.workspace().getId(), request("Outsider", "Servicer"), outsider.user().getId()), "WORKSPACE_ACCESS_DENIED");
        assertBusinessCode(() -> studentLoanService.update(owner.workspace().getId(), created.getId(), request("Should Fail", "Servicer"), owner.user().getId()), "STUDENT_LOAN_ARCHIVED");
    }

    @Test
    void controllerWrapsJsonAndRejectsInvalidInput() throws Exception {
        String username = "student_loan_http_" + UUID.randomUUID().toString().substring(0, 8);
        UserResponse registered = authService.register(registerRequest(username));
        TokenResponse token = authService.login(loginRequest(username));
        Workspace workspace = workspaceRepository.findAllByUserId(registered.getId()).getFirst();

        mockMvc.perform(post("/api/workspaces/" + workspace.getId() + "/student-loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Federal Direct","lender":"Servicer","currentPrincipal":12000,"annualInterestRate":0.0525,"minimumMonthlyPayment":125,"plannedExtraMonthlyPayment":25,"startDate":"2026-07-01"}
                                """)
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Federal Direct"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        StudentLoan saved = studentLoanRepository.findAllByWorkspaceIdAndStatus(workspace.getId(), StudentLoanStatus.ACTIVE, org.springframework.data.domain.PageRequest.of(0, 10)).getContent().getFirst();

        mockMvc.perform(get("/api/workspaces/" + workspace.getId() + "/student-loans")
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(saved.getId().toString()));

        mockMvc.perform(put("/api/workspaces/" + workspace.getId() + "/student-loans/" + saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Bad","currentPrincipal":-1,"annualInterestRate":0,"minimumMonthlyPayment":100}
                                """)
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/workspaces/" + workspace.getId() + "/student-loans/" + saved.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"PAUSED"}
                                """)
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAUSED"));
    }

    private StudentLoanRequest request(String name, String lender) {
        StudentLoanRequest request = new StudentLoanRequest();
        request.setName(name);
        request.setLender(lender);
        request.setOriginalPrincipal(new BigDecimal("15000"));
        request.setCurrentPrincipal(new BigDecimal("12000"));
        request.setAnnualInterestRate(new BigDecimal("0.0525"));
        request.setMinimumMonthlyPayment(new BigDecimal("125"));
        request.setPlannedExtraMonthlyPayment(new BigDecimal("25"));
        request.setStartDate(java.time.LocalDate.of(2026, 7, 1));
        request.setTargetPayoffDate(java.time.LocalDate.of(2032, 7, 1));
        return request;
    }

    private TestContext createContext(String prefix, WorkspaceRole role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.saveAndFlush(User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .fullName("Student Loan API User")
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
        request.setFullName("Student Loan API User");
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
