package com.moneyflowbackend.activity;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.transaction.audit.TransactionAuditAction;
import com.moneyflowbackend.transaction.audit.TransactionAuditLog;
import com.moneyflowbackend.transaction.audit.TransactionAuditLogRepository;
import com.moneyflowbackend.transaction.audit.TransactionAuditTimelineProjection;
import com.moneyflowbackend.transaction.model.Transaction;
import com.moneyflowbackend.transaction.model.TransactionStatus;
import com.moneyflowbackend.transaction.model.TransactionType;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TransactionAuditTimelineProjectionIntegrationTests {
    private static final Instant MIN_OCCURRED_AT = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant MAX_OCCURRED_AT = Instant.parse("9999-12-31T23:59:59Z");
    private static final UUID MAX_UUID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired TransactionAuditLogRepository auditLogRepository;

    @Test
    void timelineProjectionIsWorkspaceScopedOrderedCursorBoundedAndPayloadFree() {
        TestContext owner = context("timeline_owner");
        TestContext other = context("timeline_other");
        Transaction ownerTx = transaction(owner);
        Transaction otherTx = transaction(other);
        Instant base = Instant.parse("2026-07-20T10:15:30Z");
        TransactionAuditLog oldLog = audit(owner, ownerTx, TransactionAuditAction.CREATE, base.minusSeconds(60));
        audit(owner, ownerTx, TransactionAuditAction.UPDATE, base);
        audit(owner, ownerTx, TransactionAuditAction.RESTORE, base);
        audit(other, otherTx, TransactionAuditAction.CREATE, base.plusSeconds(60));

        List<TransactionAuditTimelineProjection> firstPage = auditLogRepository.findTimelinePage(
                owner.workspace().getId(), MIN_OCCURRED_AT, MAX_OCCURRED_AT, null, MAX_OCCURRED_AT, MAX_UUID, PageRequest.of(0, 2));

        assertThat(firstPage).hasSize(2);
        assertThat(firstPage).extracting(TransactionAuditTimelineProjection::getOccurredAt)
                .containsExactly(base, base);
        assertThat(firstPage).extracting(TransactionAuditTimelineProjection::getTransactionId)
                .containsOnly(ownerTx.getId());
        assertThat(firstPage).extracting(TransactionAuditTimelineProjection::getActorUserId)
                .containsOnly(owner.user().getId());
        assertThat(firstPage.get(0).getActorDisplayName()).isEqualTo(owner.user().getFullName());

        TransactionAuditTimelineProjection cursor = firstPage.get(1);
        List<TransactionAuditTimelineProjection> secondPage = auditLogRepository.findTimelinePage(
                owner.workspace().getId(), MIN_OCCURRED_AT, MAX_OCCURRED_AT, null, cursor.getOccurredAt(), cursor.getId(), PageRequest.of(0, 2));

        assertThat(secondPage).extracting(TransactionAuditTimelineProjection::getId)
                .doesNotContain(cursor.getId())
                .containsExactly(oldLog.getId());
        assertThat(Arrays.stream(firstPage.get(0).getClass().getMethods()).map(method -> method.getName()))
                .doesNotContain("getBeforeData", "getAfterData");
    }

    @Test
    void timelineProjectionSupportsDateActorAndSizeFilters() {
        TestContext owner = context("timeline_filter_owner");
        User secondActor = user("timeline_filter_actor");
        Transaction tx = transaction(owner);
        Instant base = Instant.parse("2026-07-20T10:15:30Z");
        audit(owner, tx, owner.user(), TransactionAuditAction.CREATE, base.minusSeconds(60));
        TransactionAuditLog expected = audit(owner, tx, secondActor, TransactionAuditAction.UPDATE, base);
        audit(owner, tx, owner.user(), TransactionAuditAction.RESTORE, base.plusSeconds(60));

        List<TransactionAuditTimelineProjection> rows = auditLogRepository.findTimelinePage(
                owner.workspace().getId(),
                base.minusSeconds(1),
                base.plusSeconds(1),
                secondActor.getId(),
                MAX_OCCURRED_AT,
                MAX_UUID,
                PageRequest.of(0, 2));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getId()).isEqualTo(expected.getId());
        assertThat(rows.get(0).getAuditAction()).isEqualTo(TransactionAuditAction.UPDATE);
        assertThat(rows.get(0).getActorUserId()).isEqualTo(secondActor.getId());
    }

    private TransactionAuditLog audit(TestContext ctx, Transaction tx, TransactionAuditAction action, Instant createdAt) {
        return audit(ctx, tx, ctx.user(), action, createdAt);
    }

    private TransactionAuditLog audit(TestContext ctx, Transaction tx, User actor, TransactionAuditAction action, Instant createdAt) {
        return auditLogRepository.saveAndFlush(TransactionAuditLog.builder()
                .workspace(ctx.workspace())
                .transaction(tx)
                .actorUser(actor)
                .action(action)
                .beforeData(null)
                .afterData(null)
                .createdAt(createdAt)
                .build());
    }

    private Transaction transaction(TestContext ctx) {
        Wallet wallet = walletRepository.saveAndFlush(Wallet.builder()
                .workspace(ctx.workspace())
                .name("Cash " + UUID.randomUUID())
                .walletType(WalletType.CASH)
                .openingBalance(BigDecimal.ZERO)
                .openingDate(LocalDate.of(2026, 7, 1))
                .build());
        Category category = categoryRepository.saveAndFlush(Category.builder()
                .workspace(ctx.workspace())
                .name("Food " + UUID.randomUUID())
                .categoryType(CategoryType.EXPENSE)
                .build());
        return transactionRepository.saveAndFlush(Transaction.builder()
                .workspace(ctx.workspace())
                .createdByUser(ctx.user())
                .wallet(wallet)
                .category(category)
                .transactionType(TransactionType.EXPENSE)
                .transactionStatus(TransactionStatus.POSTED)
                .amount(new BigDecimal("100.00"))
                .transactionDate(LocalDate.of(2026, 7, 20))
                .build());
    }

    private TestContext context(String username) {
        User user = user(username);
        Workspace workspace = workspaceRepository.saveAndFlush(Workspace.builder()
                .name("Workspace " + username)
                .createdByUser(user)
                .build());
        workspaceMemberRepository.saveAndFlush(WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(WorkspaceRole.OWNER)
                .build());
        return new TestContext(user, workspace);
    }

    private User user(String username) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.saveAndFlush(User.builder()
                .username((username + "_" + suffix).substring(0, Math.min(40, username.length() + 9)))
                .email(username + "@example.test")
                .fullName("User " + username)
                .build());
    }

    private record TestContext(User user, Workspace workspace) {
    }
}
