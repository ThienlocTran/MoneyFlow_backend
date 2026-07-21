package com.moneyflowbackend.activity;

import com.moneyflowbackend.activity.domain.ActivityAction;
import com.moneyflowbackend.activity.domain.ActivityActorType;
import com.moneyflowbackend.activity.domain.ActivityEntityType;
import com.moneyflowbackend.activity.domain.ActivitySource;
import com.moneyflowbackend.activity.internal.ActivityCandidate;
import com.moneyflowbackend.activity.query.ActivityCursor;
import com.moneyflowbackend.activity.query.ActivityTimelineQuery;
import com.moneyflowbackend.activity.source.DailyClosingActivitySourceReader;
import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.closing.model.DailyClosing;
import com.moneyflowbackend.closing.model.DailyClosingStatus;
import com.moneyflowbackend.closing.repository.DailyClosingRepository;
import com.moneyflowbackend.wallet.model.BalanceSourceType;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.model.WalletBalanceSnapshot;
import com.moneyflowbackend.wallet.model.WalletType;
import com.moneyflowbackend.wallet.repository.WalletBalanceSnapshotRepository;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DailyClosingActivitySourceReaderIntegrationTests {
    @Autowired DailyClosingActivitySourceReader reader;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired DailyClosingRepository dailyClosingRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired WalletBalanceSnapshotRepository snapshotRepository;

    @Test
    void mapsCompletedClosingsOnlyWithActorDetailsAndNoSnapshotSpam() {
        TestContext ctx = context("daily_closing_activity");
        Instant completedAt = Instant.parse("2026-07-20T10:15:30Z");
        DailyClosing open = closing(ctx, LocalDate.of(2026, 7, 19), null, null);
        DailyClosing completed = closing(ctx, LocalDate.of(2026, 7, 20), completedAt, ctx.user());
        snapshot(ctx, completed, wallet(ctx));
        snapshot(ctx, completed, wallet(ctx));

        List<ActivityCandidate> candidates = reader.read(query(ctx.workspace().getId()), 10);

        assertThat(open.getStatus()).isEqualTo(DailyClosingStatus.OPEN);
        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.activityId()).isEqualTo("DAILY_CLOSING:" + completed.getId());
            assertThat(candidate.action()).isEqualTo(ActivityAction.DAILY_CLOSING_COMPLETED);
            assertThat(candidate.entityType()).isEqualTo(ActivityEntityType.DAILY_CLOSING);
            assertThat(candidate.entityId()).isEqualTo(completed.getId());
            assertThat(candidate.occurredAt()).isEqualTo(completedAt);
            assertThat(candidate.businessDate()).isEqualTo(LocalDate.of(2026, 7, 20));
            assertThat(candidate.actor().type()).isEqualTo(ActivityActorType.USER);
            assertThat(candidate.actor().id()).isEqualTo(ctx.user().getId());
            assertThat(candidate.source()).isEqualTo(ActivitySource.DAILY_CLOSING);
            assertThat(candidate.details()).containsEntry("closingDate", LocalDate.of(2026, 7, 20));
            assertThat(candidate.details()).containsEntry("snapshotCount", 2L);
        });
    }

    @Test
    void filtersAndCursorFollowGlobalTupleOrder() {
        TestContext ctx = context("daily_closing_cursor");
        Instant same = Instant.parse("2026-07-20T10:15:30Z");
        closing(ctx, LocalDate.of(2026, 7, 20), same, ctx.user());
        closing(ctx, LocalDate.of(2026, 7, 21), same, ctx.user());

        List<ActivityCandidate> all = reader.read(query(ctx.workspace().getId()), 10);
        List<ActivityCandidate> firstPage = reader.read(query(ctx.workspace().getId()), 1);
        ActivityTimelineQuery secondPageQuery = new ActivityTimelineQuery(
                ctx.workspace().getId(),
                Set.of(ActivityAction.DAILY_CLOSING_COMPLETED),
                Set.of(ActivityEntityType.DAILY_CLOSING),
                ctx.user().getId(),
                same.minusSeconds(1),
                same.plusSeconds(1),
                new ActivityCursor(same, ActivitySource.DAILY_CLOSING, firstPage.getFirst().activityId()),
                1);

        List<ActivityCandidate> secondPage = reader.read(secondPageQuery, 1);

        assertThat(all).hasSize(2);
        assertThat(firstPage).singleElement().extracting(ActivityCandidate::activityId).isEqualTo(all.get(0).activityId());
        assertThat(secondPage).singleElement().extracting(ActivityCandidate::activityId).isEqualTo(all.get(1).activityId());
    }

    @Test
    void unsupportedFiltersReturnEmptyWithoutQuerySideEffects() {
        TestContext ctx = context("daily_closing_filters");
        closing(ctx, LocalDate.of(2026, 7, 20), Instant.parse("2026-07-20T10:15:30Z"), ctx.user());

        ActivityTimelineQuery query = new ActivityTimelineQuery(
                ctx.workspace().getId(),
                Set.of(ActivityAction.TRANSACTION_CREATED),
                Set.of(ActivityEntityType.TRANSACTION),
                null,
                null,
                null,
                null,
                30);

        assertThat(reader.read(query, 10)).isEmpty();
    }

    private ActivityTimelineQuery query(UUID workspaceId) {
        return new ActivityTimelineQuery(workspaceId, null, null, null, null, null, null, 30);
    }

    private DailyClosing closing(TestContext ctx, LocalDate closingDate, Instant completedAt, User completedBy) {
        return dailyClosingRepository.saveAndFlush(DailyClosing.builder()
                .workspace(ctx.workspace())
                .closingDate(closingDate)
                .status(completedAt == null ? DailyClosingStatus.OPEN : DailyClosingStatus.COMPLETED)
                .completedAt(completedAt)
                .completedBy(completedBy)
                .build());
    }

    private Wallet wallet(TestContext ctx) {
        return walletRepository.saveAndFlush(Wallet.builder()
                .workspace(ctx.workspace())
                .name("Activity closing wallet " + UUID.randomUUID())
                .walletType(WalletType.CASH)
                .openingBalance(BigDecimal.ZERO)
                .openingDate(LocalDate.of(2026, 7, 1))
                .build());
    }

    private WalletBalanceSnapshot snapshot(TestContext ctx, DailyClosing closing, Wallet wallet) {
        return snapshotRepository.saveAndFlush(WalletBalanceSnapshot.builder()
                .workspace(ctx.workspace())
                .wallet(wallet)
                .dailyClosing(closing)
                .snapshotDate(closing.getClosingDate())
                .balance(BigDecimal.TEN)
                .sourceType(BalanceSourceType.MANUAL)
                .createdBy(ctx.user())
                .createdAt(Instant.parse("2026-07-20T10:00:00Z"))
                .build());
    }

    private TestContext context(String username) {
        User user = userRepository.saveAndFlush(User.builder()
                .username(username + "_" + UUID.randomUUID().toString().substring(0, 8))
                .email(username + "_" + UUID.randomUUID() + "@example.test")
                .fullName("User " + username)
                .build());
        Workspace workspace = workspaceRepository.saveAndFlush(Workspace.builder()
                .name("Workspace " + username)
                .createdByUser(user)
                .build());
        workspaceMemberRepository.saveAndFlush(WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(WorkspaceRole.OWNER)
                .memberStatus("ACTIVE")
                .build());
        return new TestContext(user, workspace);
    }

    private record TestContext(User user, Workspace workspace) {
    }
}
