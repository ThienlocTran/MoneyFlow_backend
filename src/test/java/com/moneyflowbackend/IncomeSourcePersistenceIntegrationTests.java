package com.moneyflowbackend;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.income.model.IncomeSource;
import com.moneyflowbackend.income.model.IncomeSourceStatus;
import com.moneyflowbackend.income.model.IncomeSourceType;
import com.moneyflowbackend.income.repository.IncomeSourceRepository;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IncomeSourcePersistenceIntegrationTests {
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired IncomeSourceRepository incomeSourceRepository;
    @Autowired EntityManager entityManager;

    @Test
    void persistsActiveIncomeSourceWithNullableFieldsAndRelationships() {
        TestContext ctx = createContext("income_source_persist");

        IncomeSource source = incomeSourceRepository.saveAndFlush(IncomeSource.builder()
                .workspace(ctx.workspace())
                .name("  Salary ABC  ")
                .createdByUser(ctx.user())
                .build());
        entityManager.clear();

        IncomeSource persisted = incomeSourceRepository.findById(source.getId()).orElseThrow();
        assertThat(persisted.getName()).isEqualTo("Salary ABC");
        assertThat(persisted.getStatus()).isEqualTo(IncomeSourceStatus.ACTIVE);
        assertThat(persisted.getType()).isNull();
        assertThat(persisted.getDescription()).isNull();
        assertThat(persisted.getWorkspace().getId()).isEqualTo(ctx.workspace().getId());
        assertThat(persisted.getCreatedByUser().getId()).isEqualTo(ctx.user().getId());
        assertThat(persisted.getVersion()).isNotNull();
    }

    @Test
    void persistsAndLoadsValidTypeAndNullableDescription() {
        TestContext ctx = createContext("income_source_type");

        IncomeSource source = incomeSourceRepository.saveAndFlush(IncomeSource.builder()
                .workspace(ctx.workspace())
                .name("Freelance Design")
                .type(IncomeSourceType.FREELANCE)
                .description("   ")
                .createdByUser(ctx.user())
                .build());
        entityManager.clear();

        IncomeSource persisted = incomeSourceRepository.findById(source.getId()).orElseThrow();
        assertThat(persisted.getType()).isEqualTo(IncomeSourceType.FREELANCE);
        assertThat(persisted.getDescription()).isNull();
    }

    @Test
    void versionIncrementsOnUpdate() {
        TestContext ctx = createContext("income_source_version");
        IncomeSource source = incomeSourceRepository.saveAndFlush(incomeSource(ctx, "Rental"));
        Long firstVersion = source.getVersion();

        source.setDescription("Room rental");
        incomeSourceRepository.saveAndFlush(source);

        assertThat(source.getVersion()).isGreaterThan(firstVersion);
    }

    @Test
    void activeNameCollisionIsDetectedWithinWorkspaceIgnoringCaseAndWhitespace() {
        TestContext ctx = createContext("income_source_unique");
        incomeSourceRepository.saveAndFlush(incomeSource(ctx, "Chay Be"));

        assertThat(incomeSourceRepository.existsActiveNameInWorkspace(ctx.workspace().getId(), " CHAY BE ")).isTrue();
    }

    @Test
    void activeNameCanRepeatAcrossWorkspacesAndArchivedDoesNotBlockReuse() {
        TestContext first = createContext("income_source_reuse_a");
        TestContext second = createContext("income_source_reuse_b");
        IncomeSource archived = incomeSource(first, "Online Store");
        archived.setStatus(IncomeSourceStatus.ARCHIVED);

        incomeSourceRepository.saveAndFlush(archived);
        incomeSourceRepository.saveAndFlush(incomeSource(first, " online store "));
        incomeSourceRepository.saveAndFlush(incomeSource(second, "ONLINE STORE"));

        assertThat(incomeSourceRepository.findAllByWorkspaceIdAndStatusOrderByNameAsc(first.workspace().getId(), IncomeSourceStatus.ACTIVE))
                .extracting(IncomeSource::getName)
                .containsExactly("online store");
    }

    @Test
    void blankNameIsRejected() {
        TestContext ctx = createContext("income_source_constraints");

        assertRejected(() -> incomeSourceRepository.saveAndFlush(incomeSource(ctx, "   ")));
    }

    @Test
    void invalidTypeIsRejected() {
        TestContext ctx = createContext("income_source_bad_type");

        assertRejected(() -> insertNative(ctx, "Invalid Type", "NOT_A_TYPE", "ACTIVE"));
    }

    @Test
    void invalidStatusIsRejected() {
        TestContext ctx = createContext("income_source_bad_status");

        assertRejected(() -> insertNative(ctx, "Invalid Status", "OTHER", "DELETED"));
    }

    @Test
    void repositoryScopesLookupsToWorkspace() {
        TestContext owner = createContext("income_source_scope_owner");
        TestContext other = createContext("income_source_scope_other");
        IncomeSource source = incomeSourceRepository.saveAndFlush(incomeSource(owner, "Salary"));

        assertThat(incomeSourceRepository.findByIdAndWorkspaceId(source.getId(), owner.workspace().getId())).isPresent();
        assertThat(incomeSourceRepository.findByIdAndWorkspaceId(source.getId(), other.workspace().getId())).isEmpty();
    }

    private TestContext createContext(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .fullName("Income Source Test User")
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

    private IncomeSource incomeSource(TestContext ctx, String name) {
        return IncomeSource.builder()
                .workspace(ctx.workspace())
                .name(name)
                .type(IncomeSourceType.OTHER)
                .description("Managed by user")
                .createdByUser(ctx.user())
                .build();
    }

    private void insertNative(TestContext ctx, String name, String type, String status) {
        entityManager.createNativeQuery("""
                INSERT INTO income_sources (id, workspace_id, name, type, status, created_by_user_id, created_at, updated_at, version)
                VALUES (gen_random_uuid(), :workspaceId, :name, :type, :status, :userId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """)
                .setParameter("workspaceId", ctx.workspace().getId())
                .setParameter("name", name)
                .setParameter("type", type)
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
