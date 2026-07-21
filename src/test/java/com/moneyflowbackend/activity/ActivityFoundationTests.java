package com.moneyflowbackend.activity;

import com.moneyflowbackend.activity.domain.ActivityAction;
import com.moneyflowbackend.activity.domain.ActivityActorType;
import com.moneyflowbackend.activity.domain.ActivityEntityType;
import com.moneyflowbackend.activity.domain.ActivitySource;
import com.moneyflowbackend.activity.dto.ActivityActorSummary;
import com.moneyflowbackend.activity.dto.ActivityNavigationTarget;
import com.moneyflowbackend.activity.internal.ActivityCandidate;
import com.moneyflowbackend.activity.internal.ActivitySafeDetails;
import com.moneyflowbackend.activity.internal.ActivityTimelineOrdering;
import com.moneyflowbackend.activity.query.ActivityCursor;
import com.moneyflowbackend.activity.query.ActivityCursorCodec;
import com.moneyflowbackend.activity.query.ActivityIdFactory;
import com.moneyflowbackend.activity.query.ActivityTimelineQuery;
import com.moneyflowbackend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActivityFoundationTests {
    private final ActivityCursorCodec codec = new ActivityCursorCodec();

    @Test
    void activityIdIsStableAndSourceScoped() {
        UUID sourceId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        assertThat(ActivityIdFactory.stableId(ActivitySource.TRANSACTION_AUDIT, sourceId))
                .isEqualTo(ActivityIdFactory.stableId(ActivitySource.TRANSACTION_AUDIT, sourceId))
                .isEqualTo("TRANSACTION_AUDIT:00000000-0000-0000-0000-000000000001");
        assertThat(ActivityIdFactory.stableId(ActivitySource.TRANSACTION, sourceId))
                .isNotEqualTo(ActivityIdFactory.stableId(ActivitySource.TRANSACTION_AUDIT, sourceId));
        assertThatThrownBy(() -> ActivityIdFactory.stableId(null, sourceId))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ActivityIdFactory.stableId(ActivitySource.TRANSACTION_AUDIT, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cursorRoundTripsAsUrlSafeOpaqueValue() {
        ActivityCursor cursor = new ActivityCursor(
                Instant.parse("2026-07-20T10:15:30Z"),
                ActivitySource.TRANSACTION_AUDIT,
                "TRANSACTION_AUDIT:00000000-0000-0000-0000-000000000001");

        String encoded = codec.encode(cursor);

        assertThat(encoded).doesNotContain("+", "/", "=");
        assertThat(codec.decode(encoded)).isEqualTo(cursor);
    }

    @Test
    void cursorRejectsMalformedPayloadsAsDomainError() {
        assertInvalidCursor("not base64");
        assertInvalidCursor(encodePayload("broken"));
        assertInvalidCursor(encodePayload("v=2\noccurredAt=2026-07-20T10:15:30Z\nsource=TRANSACTION_AUDIT\nstableId=TRANSACTION_AUDIT:00000000-0000-0000-0000-000000000001"));
        assertInvalidCursor(encodePayload("v=1\nsource=TRANSACTION_AUDIT\nstableId=TRANSACTION_AUDIT:00000000-0000-0000-0000-000000000001"));
        assertInvalidCursor(encodePayload("v=1\noccurredAt=2026-07-20T10:15:30Z\nsource=BOGUS\nstableId=TRANSACTION_AUDIT:00000000-0000-0000-0000-000000000001"));
        assertInvalidCursor(encodePayload("v=1\noccurredAt=2026-07-20T10:15:30Z\nsource=TRANSACTION_AUDIT\nstableId=TRANSACTION_AUDIT:not-a-uuid"));
        assertInvalidCursor(encodePayload("v=1\noccurredAt=2026-07-20T10:15:30Z\nsource=TRANSACTION_AUDIT\nstableId=TRANSACTION:00000000-0000-0000-0000-000000000001"));
    }

    @Test
    void orderingIsDeterministicAndCursorBoundaryDoesNotRepeatLastItem() {
        UUID workspaceId = UUID.fromString("00000000-0000-0000-0000-000000000099");
        Instant sameTime = Instant.parse("2026-07-20T10:15:30Z");
        ActivityCandidate latest = candidate(workspaceId, "TRANSACTION_AUDIT:ffffffff-ffff-ffff-ffff-ffffffffffff", sameTime, ActivitySource.TRANSACTION_AUDIT);
        ActivityCandidate lowerStableId = candidate(workspaceId, "TRANSACTION_AUDIT:00000000-0000-0000-0000-000000000001", sameTime, ActivitySource.TRANSACTION_AUDIT);
        ActivityCandidate lowerRank = candidate(workspaceId, "TRANSACTION:ffffffff-ffff-ffff-ffff-ffffffffffff", sameTime, ActivitySource.TRANSACTION);
        ActivityCandidate older = candidate(workspaceId, "TRANSACTION_AUDIT:ffffffff-ffff-ffff-ffff-ffffffffffff", sameTime.minusSeconds(1), ActivitySource.TRANSACTION_AUDIT);

        assertThat(List.of(older, lowerRank, lowerStableId, latest).stream()
                .sorted(ActivityCandidate.ORDERING)
                .map(ActivityCandidate::activityId))
                .containsExactly(
                        latest.activityId(),
                        lowerStableId.activityId(),
                        lowerRank.activityId(),
                        older.activityId());

        ActivityCursor cursor = new ActivityCursor(latest.occurredAt(), latest.source(), latest.activityId());
        assertThat(ActivityTimelineOrdering.isAfterCursor(latest, cursor)).isFalse();
        assertThat(ActivityTimelineOrdering.isAfterCursor(lowerStableId, cursor)).isTrue();
        assertThat(ActivityTimelineOrdering.isAfterCursor(older, cursor)).isTrue();
    }

    @Test
    void queryCriteriaDefaultsValidatesAndDeduplicatesFilters() {
        UUID workspaceId = UUID.randomUUID();

        ActivityTimelineQuery defaults = new ActivityTimelineQuery(workspaceId, null, null, null, null, null, null, ActivityTimelineQuery.DEFAULT_SIZE);
        assertThat(defaults.size()).isEqualTo(ActivityTimelineQuery.DEFAULT_SIZE);
        assertThat(defaults.actions()).isEmpty();
        assertThat(defaults.entityTypes()).isEmpty();

        ActivityTimelineQuery max = new ActivityTimelineQuery(
                workspaceId,
                Set.of(ActivityAction.TRANSACTION_CREATED),
                Set.of(ActivityEntityType.TRANSACTION),
                UUID.randomUUID(),
                Instant.parse("2026-07-20T00:00:00Z"),
                Instant.parse("2026-07-21T00:00:00Z"),
                null,
                ActivityTimelineQuery.MAX_SIZE);
        assertThat(max.actions()).containsExactly(ActivityAction.TRANSACTION_CREATED);

        assertBusinessCode(() -> new ActivityTimelineQuery(workspaceId, null, null, null, null, null, null, 0), "INVALID_ACTIVITY_PAGE_SIZE");
        assertBusinessCode(() -> new ActivityTimelineQuery(workspaceId, null, null, null, null, null, null, -1), "INVALID_ACTIVITY_PAGE_SIZE");
        assertBusinessCode(() -> new ActivityTimelineQuery(workspaceId, null, null, null, null, null, null, 101), "INVALID_ACTIVITY_PAGE_SIZE");
        assertBusinessCode(() -> new ActivityTimelineQuery(workspaceId, null, null, null,
                Instant.parse("2026-07-21T00:00:00Z"), Instant.parse("2026-07-21T00:00:00Z"), null, 30), "INVALID_ACTIVITY_DATE_RANGE");
    }

    @Test
    void actorTypesDoNotPretendMissingUserIsSystem() {
        UUID userId = UUID.randomUUID();

        assertThat(ActivityActorSummary.user(userId, "Loc"))
                .extracting(ActivityActorSummary::type, ActivityActorSummary::id, ActivityActorSummary::displayName)
                .containsExactly(ActivityActorType.USER, userId, "Loc");
        assertThat(ActivityActorSummary.user(null, "Deleted")).isEqualTo(ActivityActorSummary.unknown());
        assertThat(ActivityActorSummary.system().type()).isEqualTo(ActivityActorType.SYSTEM);
        assertThat(ActivityActorSummary.unknown().type()).isEqualTo(ActivityActorType.UNKNOWN);
    }

    @Test
    void safeDetailsOnlyKeepsWhitelistedMetadata() {
        Map<String, Object> safe = ActivitySafeDetails.whitelist(Map.of(
                "transactionType", "EXPENSE",
                "transactionStatus", "POSTED",
                "walletId", UUID.randomUUID(),
                "note", "private free text",
                "rawInput", "voice text",
                "token", "secret",
                "beforeState", Map.of("amount", "1"),
                "audioUrl", "https://storage.example/private"));

        assertThat(safe).containsKeys("transactionType", "transactionStatus", "walletId");
        assertThat(safe).doesNotContainKeys("note", "rawInput", "token", "beforeState", "audioUrl");
    }

    private ActivityCandidate candidate(UUID workspaceId, String id, Instant occurredAt, ActivitySource source) {
        return new ActivityCandidate(
                id,
                workspaceId,
                occurredAt,
                source.rank(),
                source,
                ActivityActorSummary.unknown(),
                ActivityAction.TRANSACTION_CREATED,
                ActivityEntityType.TRANSACTION,
                UUID.randomUUID(),
                null,
                null,
                LocalDate.of(2026, 7, 20),
                ActivityNavigationTarget.none(),
                Map.of());
    }

    private void assertInvalidCursor(String value) {
        assertBusinessCode(() -> codec.decode(value), "INVALID_ACTIVITY_CURSOR");
    }

    private void assertBusinessCode(Runnable runnable, String code) {
        assertThatThrownBy(runnable::run)
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(code);
    }

    private String encodePayload(String payload) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes());
    }
}
