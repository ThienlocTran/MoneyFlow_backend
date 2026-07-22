package com.moneyflowbackend;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.sinkingfund.model.SinkingFund;
import com.moneyflowbackend.sinkingfund.model.SinkingFundAllocation;
import com.moneyflowbackend.sinkingfund.model.SinkingFundAllocationType;
import com.moneyflowbackend.sinkingfund.model.SinkingFundStatus;
import com.moneyflowbackend.sinkingfund.repository.SinkingFundAllocationRepository;
import com.moneyflowbackend.sinkingfund.repository.SinkingFundRepository;
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
class SinkingFundPersistenceIntegrationTests {
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired SinkingFundRepository sinkingFundRepository;
    @Autowired SinkingFundAllocationRepository allocationRepository;
    @Autowired EntityManager entityManager;

    @Test
    void persistsFundWithWorkspaceActorOptimisticLockingAndTrimmedText() {
        TestContext ctx = createContext("sinking_fund_persist");

        SinkingFund fund = sinkingFundRepository.saveAndFlush(SinkingFund.builder()
                .workspace(ctx.workspace())
                .name("  Annual Insurance  ")
                .description("  Due in December  ")
                .targetAmount(new BigDecimal("1200000.00"))
                .targetDate(LocalDate.of(2027, 12, 1))
                .createdByUser(ctx.user())
                .build());
        entityManager.clear();

        SinkingFund persisted = sinkingFundRepository.findById(fund.getId()).orElseThrow();
        assertThat(persisted.getName()).isEqualTo("Annual Insurance");
        assertThat(persisted.getDescription()).isEqualTo("Due in December");
        assertThat(persisted.getStatus()).isEqualTo(SinkingFundStatus.ACTIVE);
        assertThat(persisted.getWorkspace().getId()).isEqualTo(ctx.workspace().getId());
        assertThat(persisted.getCreatedByUser().getId()).isEqualTo(ctx.user().getId());
        assertThat(persisted.getVersion()).isNotNull();
    }

    @Test
    void derivesReservedAmountFromExplicitAllocationRows() {
        TestContext ctx = createContext("sinking_fund_sum");
        SinkingFund fund = sinkingFundRepository.saveAndFlush(fund(ctx, "Wi-Fi renewal"));
        allocationRepository.saveAndFlush(allocation(ctx, fund, SinkingFundAllocationType.ALLOCATE, "200000.00"));
        allocationRepository.saveAndFlush(allocation(ctx, fund, SinkingFundAllocationType.RELEASE, "-50000.00"));

        assertThat(allocationRepository.sumReservedAmount(ctx.workspace().getId(), fund.getId()))
                .isEqualByComparingTo("150000.00");
    }

    @Test
    void repositoryScopesFundsToWorkspace() {
        TestContext owner = createContext("sinking_fund_scope_owner");
        TestContext other = createContext("sinking_fund_scope_other");
        SinkingFund fund = sinkingFundRepository.saveAndFlush(fund(owner, "Laptop"));

        assertThat(sinkingFundRepository.findByIdAndWorkspaceId(fund.getId(), owner.workspace().getId())).isPresent();
        assertThat(sinkingFundRepository.findByIdAndWorkspaceId(fund.getId(), other.workspace().getId())).isEmpty();
    }

    @Test
    void openFundNameCollisionIsDetectedWithinWorkspaceIgnoringCaseAndWhitespace() {
        TestContext ctx = createContext("sinking_fund_unique");
        sinkingFundRepository.saveAndFlush(fund(ctx, "Insurance"));

        assertThat(sinkingFundRepository.existsOpenNameInWorkspace(ctx.workspace().getId(), " insurance ")).isTrue();
    }

    @Test
    void archivedFundNameCanBeReusedAndOpenDuplicateIsDetected() {
        TestContext ctx = createContext("sinking_fund_reuse");
        SinkingFund archived = fund(ctx, "Phone");
        archived.setStatus(SinkingFundStatus.ARCHIVED);
        sinkingFundRepository.saveAndFlush(archived);
        sinkingFundRepository.saveAndFlush(fund(ctx, " phone "));

        assertThat(sinkingFundRepository.existsOpenNameInWorkspace(ctx.workspace().getId(), "PHONE")).isTrue();
    }

    @Test
    void invalidFundAndAllocationValuesAreRejected() {
        TestContext ctx = createContext("sinking_fund_constraints");
        SinkingFund fund = sinkingFundRepository.saveAndFlush(fund(ctx, "Emergency buffer"));

        assertRejected(() -> sinkingFundRepository.saveAndFlush(fund(ctx, "   ")));
        assertRejected(() -> insertFundNative(ctx, "Bad status", "DELETED", "1.00"));
        assertRejected(() -> insertFundNative(ctx, "Bad target", "ACTIVE", "0.00"));
        assertRejected(() -> allocationRepository.saveAndFlush(allocation(ctx, fund, SinkingFundAllocationType.ADJUST, "0.00")));
    }

    private TestContext createContext(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .fullName("Sinking Fund Test User")
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

    private SinkingFund fund(TestContext ctx, String name) {
        return SinkingFund.builder()
                .workspace(ctx.workspace())
                .name(name)
                .targetAmount(new BigDecimal("1000000.00"))
                .createdByUser(ctx.user())
                .build();
    }

    private SinkingFundAllocation allocation(TestContext ctx, SinkingFund fund, SinkingFundAllocationType type, String delta) {
        return SinkingFundAllocation.builder()
                .workspace(ctx.workspace())
                .sinkingFund(fund)
                .allocationType(type)
                .amountDelta(new BigDecimal(delta))
                .actorUser(ctx.user())
                .build();
    }

    private void insertFundNative(TestContext ctx, String name, String status, String targetAmount) {
        entityManager.createNativeQuery("""
                INSERT INTO sinking_funds (id, workspace_id, name, target_amount, status, created_by_user_id, created_at, updated_at, version)
                VALUES (gen_random_uuid(), :workspaceId, :name, :targetAmount, :status, :userId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """)
                .setParameter("workspaceId", ctx.workspace().getId())
                .setParameter("name", name)
                .setParameter("targetAmount", new BigDecimal(targetAmount))
                .setParameter("status", status)
                .setParameter("userId", ctx.user().getId())
                .executeUpdate();
        entityManager.flush();
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
