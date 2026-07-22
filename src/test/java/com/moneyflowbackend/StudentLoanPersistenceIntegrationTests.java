package com.moneyflowbackend;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.studentloan.model.StudentLoan;
import com.moneyflowbackend.studentloan.model.StudentLoanStatus;
import com.moneyflowbackend.studentloan.repository.StudentLoanRepository;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.DataException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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
class StudentLoanPersistenceIntegrationTests {
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired StudentLoanRepository studentLoanRepository;
    @Autowired EntityManager entityManager;

    @Test
    void persistsLoanPlanningFieldsAndVersion() {
        TestContext ctx = createContext("student_loan_persist");

        StudentLoan loan = studentLoanRepository.saveAndFlush(loan(ctx, "  Federal Loan A  "));
        entityManager.clear();

        StudentLoan persisted = studentLoanRepository.findById(loan.getId()).orElseThrow();
        assertThat(persisted.getName()).isEqualTo("Federal Loan A");
        assertThat(persisted.getLender()).isEqualTo("Servicer");
        assertThat(persisted.getStatus()).isEqualTo(StudentLoanStatus.ACTIVE);
        assertThat(persisted.getAnnualInterestRate()).isEqualByComparingTo("0.052500");
        assertThat(persisted.getWorkspace().getId()).isEqualTo(ctx.workspace().getId());
        assertThat(persisted.getCreatedByUser().getId()).isEqualTo(ctx.user().getId());
        assertThat(persisted.getVersion()).isNotNull();
    }

    @Test
    void zeroInterestAndZeroPrincipalAreAllowedForPlanningHistory() {
        TestContext ctx = createContext("student_loan_zero");
        StudentLoan loan = loan(ctx, "Zero Loan");
        loan.setCurrentPrincipal(BigDecimal.ZERO);
        loan.setAnnualInterestRate(BigDecimal.ZERO);
        loan.setStatus(StudentLoanStatus.PAID_OFF);

        StudentLoan saved = studentLoanRepository.saveAndFlush(loan);

        assertThat(studentLoanRepository.findByIdAndWorkspaceId(saved.getId(), ctx.workspace().getId())).isPresent();
    }

    @Test
    void invalidMoneyRateAndDatesAreRejected() {
        TestContext ctx = createContext("student_loan_invalid");

        StudentLoan badPrincipal = loan(ctx, "Bad Principal");
        badPrincipal.setCurrentPrincipal(new BigDecimal("-1.00"));
        assertRejected(() -> studentLoanRepository.saveAndFlush(badPrincipal));

        StudentLoan badRate = loan(ctx, "Bad Rate");
        badRate.setAnnualInterestRate(new BigDecimal("-0.01"));
        assertRejected(() -> studentLoanRepository.saveAndFlush(badRate));

        StudentLoan badDate = loan(ctx, "Bad Date");
        badDate.setStartDate(LocalDate.of(2026, 1, 1));
        badDate.setTargetPayoffDate(LocalDate.of(2025, 12, 31));
        assertRejected(() -> studentLoanRepository.saveAndFlush(badDate));
    }

    @Test
    void repositoryScopesLookupsToWorkspace() {
        TestContext owner = createContext("student_loan_scope_owner");
        TestContext other = createContext("student_loan_scope_other");
        StudentLoan loan = studentLoanRepository.saveAndFlush(loan(owner, "Scoped Loan"));

        assertThat(studentLoanRepository.findByIdAndWorkspaceId(loan.getId(), owner.workspace().getId())).isPresent();
        assertThat(studentLoanRepository.findByIdAndWorkspaceId(loan.getId(), other.workspace().getId())).isEmpty();
    }

    private TestContext createContext(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .fullName("Student Loan Test User")
                .build());
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .name(prefix + " workspace")
                .createdByUser(user)
                .build());
        workspaceMemberRepository.save(WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(WorkspaceRole.OWNER)
                .build());
        return new TestContext(user, workspace);
    }

    private StudentLoan loan(TestContext ctx, String name) {
        return StudentLoan.builder()
                .workspace(ctx.workspace())
                .name(name)
                .lender("  Servicer  ")
                .originalPrincipal(new BigDecimal("12000.00"))
                .currentPrincipal(new BigDecimal("10000.00"))
                .annualInterestRate(new BigDecimal("0.052500"))
                .minimumMonthlyPayment(new BigDecimal("125.00"))
                .plannedExtraMonthlyPayment(new BigDecimal("25.00"))
                .startDate(LocalDate.of(2026, 7, 1))
                .targetPayoffDate(LocalDate.of(2032, 7, 1))
                .createdByUser(ctx.user())
                .build();
    }

    private void assertRejected(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfAny(
                DataIntegrityViolationException.class,
                ConstraintViolationException.class,
                DataException.class);
    }

    private record TestContext(User user, Workspace workspace) {
    }
}
