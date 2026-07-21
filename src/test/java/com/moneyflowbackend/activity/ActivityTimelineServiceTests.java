package com.moneyflowbackend.activity;

import com.moneyflowbackend.activity.domain.ActivityAction;
import com.moneyflowbackend.activity.domain.ActivityEntityType;
import com.moneyflowbackend.activity.domain.ActivitySource;
import com.moneyflowbackend.activity.dto.ActivityActorSummary;
import com.moneyflowbackend.activity.dto.ActivityNavigationTarget;
import com.moneyflowbackend.activity.dto.ActivityTimelinePageResponse;
import com.moneyflowbackend.activity.internal.ActivityCandidate;
import com.moneyflowbackend.activity.mapper.ActivityTimelineResponseMapper;
import com.moneyflowbackend.activity.query.ActivityCursorCodec;
import com.moneyflowbackend.activity.query.ActivityTimelineQuery;
import com.moneyflowbackend.activity.service.ActivityTimelineService;
import com.moneyflowbackend.activity.source.ActivitySourceReader;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataRetrievalFailureException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ActivityTimelineServiceTests {
    private static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private final ActivityCursorCodec codec = new ActivityCursorCodec();

    @Test
    void mergesSortsDeduplicatesAndBuildsCursorAcrossSources() {
        Instant same = Instant.parse("2026-07-20T10:15:30Z");
        CapturingReader transactionAudit = new CapturingReader(List.of(
                candidate("TRANSACTION_AUDIT:00000000-0000-0000-0000-000000000002", same, ActivitySource.TRANSACTION_AUDIT),
                candidate("TRANSACTION_AUDIT:00000000-0000-0000-0000-000000000001", same, ActivitySource.TRANSACTION_AUDIT),
                candidate("TRANSACTION_AUDIT:00000000-0000-0000-0000-000000000004", same.minusSeconds(10), ActivitySource.TRANSACTION_AUDIT)));
        CapturingReader transaction = new CapturingReader(List.of(
                candidate("TRANSACTION:00000000-0000-0000-0000-000000000003", same, ActivitySource.TRANSACTION),
                candidate("TRANSACTION_AUDIT:00000000-0000-0000-0000-000000000001", same, ActivitySource.TRANSACTION_AUDIT)));
        ActivityTimelineService service = service(transactionAudit, transaction);

        ActivityTimelinePageResponse firstPage = service.getTimeline(
                WORKSPACE_ID, Set.of(), Set.of(), null, null, null, null, 3, USER_ID);

        assertThat(transactionAudit.limit).isEqualTo(4);
        assertThat(transaction.limit).isEqualTo(4);
        assertThat(firstPage.getItems()).extracting("id").containsExactly(
                "TRANSACTION_AUDIT:00000000-0000-0000-0000-000000000002",
                "TRANSACTION_AUDIT:00000000-0000-0000-0000-000000000001",
                "TRANSACTION:00000000-0000-0000-0000-000000000003");
        assertThat(firstPage.isHasMore()).isTrue();
        assertThat(firstPage.getNextCursor()).isNotBlank();
        assertThat(firstPage.getItems()).allSatisfy(item -> assertThat(item).hasNoNullFieldsOrPropertiesExcept(
                "amount", "direction", "businessDate"));

        ActivityTimelinePageResponse secondPage = service.getTimeline(
                WORKSPACE_ID, Set.of(), Set.of(), null, null, null, firstPage.getNextCursor(), 3, USER_ID);

        assertThat(secondPage.getItems()).extracting("id")
                .doesNotContain(firstPage.getItems().getLast().getId())
                .containsExactly("TRANSACTION_AUDIT:00000000-0000-0000-0000-000000000004");
        assertThat(secondPage.isHasMore()).isFalse();
        assertThat(secondPage.getNextCursor()).isNull();
    }

    @Test
    void appliesFiltersWithAndSemanticsAfterReaderResults() {
        UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000011");
        Instant from = Instant.parse("2026-07-20T00:00:00Z");
        Instant to = Instant.parse("2026-07-21T00:00:00Z");
        CapturingReader reader = new CapturingReader(List.of(
                candidate("TRANSACTION_AUDIT:00000000-0000-0000-0000-000000000001", from.plusSeconds(1), ActivitySource.TRANSACTION_AUDIT,
                        ActivityAction.TRANSACTION_CREATED, ActivityEntityType.TRANSACTION, actorId),
                candidate("TRANSACTION_AUDIT:00000000-0000-0000-0000-000000000002", from.plusSeconds(2), ActivitySource.TRANSACTION_AUDIT,
                        ActivityAction.TRANSFER_CREATED, ActivityEntityType.TRANSFER, actorId)));
        ActivityTimelineService service = service(reader);

        ActivityTimelinePageResponse page = service.getTimeline(
                WORKSPACE_ID,
                Set.of(ActivityAction.TRANSACTION_CREATED),
                Set.of(ActivityEntityType.TRANSACTION),
                actorId,
                from,
                to,
                null,
                10,
                USER_ID);

        assertThat(reader.query.actions()).containsExactly(ActivityAction.TRANSACTION_CREATED);
        assertThat(reader.query.entityTypes()).containsExactly(ActivityEntityType.TRANSACTION);
        assertThat(reader.query.actorId()).isEqualTo(actorId);
        assertThat(reader.query.from()).isEqualTo(from);
        assertThat(reader.query.to()).isEqualTo(to);
        assertThat(page.getItems()).singleElement()
                .extracting("id")
                .isEqualTo("TRANSACTION_AUDIT:00000000-0000-0000-0000-000000000001");
    }

    @Test
    void authorizationRunsBeforeSourceReaders() {
        WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
        WorkspaceMemberRepository memberRepository = mock(WorkspaceMemberRepository.class);
        ActivitySourceReader reader = mock(ActivitySourceReader.class);
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(Workspace.builder().build()));
        when(memberRepository.findByWorkspaceIdAndUserIdAndMemberStatus(WORKSPACE_ID, USER_ID, "ACTIVE"))
                .thenReturn(Optional.empty());
        ActivityTimelineService service = new ActivityTimelineService(
                List.of(reader), codec, new ActivityTimelineResponseMapper(), workspaceRepository, memberRepository);

        assertThatThrownBy(() -> service.getTimeline(WORKSPACE_ID, Set.of(), Set.of(), null, null, null, null, 10, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("WORKSPACE_ACCESS_DENIED");
        verifyNoInteractions(reader);
    }

    @Test
    void sourceInfrastructureErrorIsNotReturnedAsPartialSuccess() {
        ActivityTimelineService service = service((query, limit) -> {
            throw new DataRetrievalFailureException("database unavailable");
        });

        assertThatThrownBy(() -> service.getTimeline(WORKSPACE_ID, Set.of(), Set.of(), null, null, null, null, 10, USER_ID))
                .isInstanceOf(DataRetrievalFailureException.class);
    }

    private ActivityTimelineService service(ActivitySourceReader... readers) {
        WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
        WorkspaceMemberRepository memberRepository = mock(WorkspaceMemberRepository.class);
        Workspace workspace = Workspace.builder().id(WORKSPACE_ID).build();
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace));
        when(memberRepository.findByWorkspaceIdAndUserIdAndMemberStatus(WORKSPACE_ID, USER_ID, "ACTIVE"))
                .thenReturn(Optional.of(WorkspaceMember.builder().workspace(workspace).role(WorkspaceRole.VIEWER).build()));
        return new ActivityTimelineService(
                List.of(readers), codec, new ActivityTimelineResponseMapper(), workspaceRepository, memberRepository);
    }

    private ActivityCandidate candidate(String id, Instant occurredAt, ActivitySource source) {
        return candidate(id, occurredAt, source, ActivityAction.TRANSACTION_CREATED, ActivityEntityType.TRANSACTION, null);
    }

    private ActivityCandidate candidate(
            String id,
            Instant occurredAt,
            ActivitySource source,
            ActivityAction action,
            ActivityEntityType entityType,
            UUID actorId) {
        return new ActivityCandidate(
                id,
                WORKSPACE_ID,
                occurredAt,
                source.rank(),
                source,
                actorId == null ? ActivityActorSummary.unknown() : ActivityActorSummary.user(actorId, "Actor"),
                action,
                entityType,
                UUID.randomUUID(),
                action == ActivityAction.TRANSFER_CREATED ? BigDecimal.TEN : null,
                action == ActivityAction.TRANSFER_CREATED ? "TRANSFER" : null,
                LocalDate.of(2026, 7, 20),
                ActivityNavigationTarget.none(),
                Map.of());
    }

    private static class CapturingReader implements ActivitySourceReader {
        private final List<ActivityCandidate> candidates;
        private ActivityTimelineQuery query;
        private int limit;

        private CapturingReader(List<ActivityCandidate> candidates) {
            this.candidates = candidates;
        }

        @Override
        public List<ActivityCandidate> read(ActivityTimelineQuery query, int limit) {
            this.query = query;
            this.limit = limit;
            return candidates.stream()
                    .filter(candidate -> query.actions().isEmpty() || query.actions().contains(candidate.action()))
                    .filter(candidate -> query.entityTypes().isEmpty() || query.entityTypes().contains(candidate.entityType()))
                    .filter(candidate -> query.actorId() == null || query.actorId().equals(candidate.actor().id()))
                    .filter(candidate -> query.from() == null || !candidate.occurredAt().isBefore(query.from()))
                    .filter(candidate -> query.to() == null || candidate.occurredAt().isBefore(query.to()))
                    .filter(candidate -> query.cursor() == null || com.moneyflowbackend.activity.internal.ActivityTimelineOrdering.isAfterCursor(candidate, query.cursor()))
                    .sorted(ActivityCandidate.ORDERING)
                    .limit(limit)
                    .toList();
        }
    }
}
