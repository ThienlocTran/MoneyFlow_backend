package com.moneyflowbackend;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.studentloan.dto.StudentLoanRequest;
import com.moneyflowbackend.studentloan.dto.StudentLoanResponse;
import com.moneyflowbackend.studentloan.dto.StudentLoanStrategyRequest;
import com.moneyflowbackend.studentloan.model.StudentLoanStatus;
import com.moneyflowbackend.studentloan.repository.StudentLoanRepository;
import com.moneyflowbackend.studentloan.service.StudentLoanProjectionCalculator;
import com.moneyflowbackend.studentloan.service.StudentLoanService;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StudentLoanReadinessIntegrationTests {
    @Autowired StudentLoanService studentLoanService;
    @Autowired StudentLoanRepository studentLoanRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;

    @Test
    void authorizationMatrixWorkspaceIsolationAndStateBehaviorAreExplicit() {
        TestContext owner = createContext("loan_ready_owner", WorkspaceRole.OWNER);
        TestContext editor = createContext("loan_ready_editor", WorkspaceRole.OWNER);
        TestContext viewer = createContext("loan_ready_viewer", WorkspaceRole.OWNER);
        TestContext outsider = createContext("loan_ready_other", WorkspaceRole.OWNER);
        workspaceMemberRepository.saveAndFlush(member(owner.workspace(), editor.user(), WorkspaceRole.EDITOR));
        workspaceMemberRepository.saveAndFlush(member(owner.workspace(), viewer.user(), WorkspaceRole.VIEWER));
        StudentLoanResponse loan = studentLoanService.create(owner.workspace().getId(), request("Ready Loan", "0.0525", "125"), owner.user().getId());

        assertThat(studentLoanService.update(owner.workspace().getId(), loan.getId(), request("Editor Update", "0.0525", "125"), editor.user().getId()).getName())
                .isEqualTo("Editor Update");
        assertThat(studentLoanService.get(owner.workspace().getId(), loan.getId(), viewer.user().getId()).getId()).isEqualTo(loan.getId());
        assertCode(() -> studentLoanService.archive(owner.workspace().getId(), loan.getId(), viewer.user().getId()), "FORBIDDEN");
        assertCode(() -> studentLoanService.get(outsider.workspace().getId(), loan.getId(), outsider.user().getId()), "STUDENT_LOAN_NOT_FOUND");

        assertThat(studentLoanService.markPaidOff(owner.workspace().getId(), loan.getId(), owner.user().getId()).getCurrentPrincipal())
                .isEqualByComparingTo("0.00");
        assertCode(() -> studentLoanService.update(owner.workspace().getId(), loan.getId(), request("Paid Update", "0.0525", "125"), owner.user().getId()), "STUDENT_LOAN_PAID_OFF");
        assertThat(studentLoanService.archive(owner.workspace().getId(), loan.getId(), owner.user().getId()).getStatus())
                .isEqualTo(StudentLoanStatus.ARCHIVED);
    }

    @Test
    void projectionsAndStrategiesHaveNoWalletOrTransactionSideEffects() {
        TestContext owner = createContext("loan_ready_side_effect", WorkspaceRole.OWNER);
        StudentLoanResponse loan = studentLoanService.create(owner.workspace().getId(), request("No Side Effects", "0.0000", "300"), owner.user().getId());
        long transactionsBefore = transactionRepository.count();
        long walletsBefore = walletRepository.count();

        assertThat(studentLoanService.projection(owner.workspace().getId(), loan.getId(), true, 0, 10, owner.user().getId()).getTotalProjectedPayments())
                .isEqualByComparingTo("12000.00");
        StudentLoanStrategyRequest strategyRequest = new StudentLoanStrategyRequest();
        strategyRequest.setExtraMonthlyBudget(new BigDecimal("200"));
        assertThat(studentLoanService.compareStrategies(owner.workspace().getId(), strategyRequest, owner.user().getId()).getResults()).hasSize(3);

        assertThat(transactionRepository.count()).isEqualTo(transactionsBefore);
        assertThat(walletRepository.count()).isEqualTo(walletsBefore);
    }

    @Test
    void nonAmortizingAndHorizonSafetyCasesAreClear() {
        TestContext owner = createContext("loan_ready_non_amortizing", WorkspaceRole.OWNER);
        StudentLoanResponse nonAmortizing = studentLoanService.create(owner.workspace().getId(), request("Too Small", "0.1200", "50"), owner.user().getId());

        assertThat(studentLoanService.projection(owner.workspace().getId(), nonAmortizing.getId(), false, 0, 10, owner.user().getId()).isNonAmortizing())
                .isTrue();
        assertThat(new StudentLoanProjectionCalculator().project(
                UUID.randomUUID(), new BigDecimal("1000000"), new BigDecimal("0.0100"),
                new BigDecimal("900"), BigDecimal.ZERO, LocalDate.of(2026, 7, 1), false, 0, 10)
                .getNonAmortizingReason()).contains("600-month");
    }

    @Test
    void productionSchemaPinAndMigrationReservationsRemainIntact() throws Exception {
        String production = Files.readString(Path.of("src/main/resources/application-production.yml"));
        assertThat(production).contains("SET search_path TO public");
        assertThat(production).contains("default-schema: public");
        assertThat(Files.exists(Path.of("src/main/resources/db/migration/V14__student_loans.sql"))).isTrue();
        assertThat(Files.exists(Path.of("src/main/resources/db/migration/V13__student_loans.sql"))).isFalse();
    }

    private StudentLoanRequest request(String name, String rate, String minimumPayment) {
        StudentLoanRequest request = new StudentLoanRequest();
        request.setName(name);
        request.setLender("Servicer");
        request.setCurrentPrincipal(new BigDecimal("12000"));
        request.setAnnualInterestRate(new BigDecimal(rate));
        request.setMinimumMonthlyPayment(new BigDecimal(minimumPayment));
        request.setStartDate(LocalDate.of(2026, 7, 1));
        return request;
    }

    private TestContext createContext(String prefix, WorkspaceRole role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.saveAndFlush(User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .fullName("Student Loan Readiness User")
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
                .extracting("code")
                .isEqualTo(code);
    }

    private record TestContext(User user, Workspace workspace) {
    }
}
