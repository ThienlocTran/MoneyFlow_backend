package com.moneyflowbackend.activity.service;

import com.moneyflowbackend.activity.domain.ActivityAction;
import com.moneyflowbackend.activity.domain.ActivityEntityType;
import com.moneyflowbackend.activity.dto.ActivityTimelinePageResponse;
import com.moneyflowbackend.activity.internal.ActivityCandidate;
import com.moneyflowbackend.activity.internal.ActivityTimelineOrdering;
import com.moneyflowbackend.activity.mapper.ActivityTimelineResponseMapper;
import com.moneyflowbackend.activity.query.ActivityCursor;
import com.moneyflowbackend.activity.query.ActivityCursorCodec;
import com.moneyflowbackend.activity.query.ActivityTimelineQuery;
import com.moneyflowbackend.activity.source.ActivitySourceReader;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ActivityTimelineService {
    private final List<ActivitySourceReader> readers;
    private final ActivityCursorCodec cursorCodec;
    private final ActivityTimelineResponseMapper mapper;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    public ActivityTimelineService(
            List<ActivitySourceReader> readers,
            ActivityCursorCodec cursorCodec,
            ActivityTimelineResponseMapper mapper,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository) {
        this.readers = List.copyOf(readers);
        this.cursorCodec = cursorCodec;
        this.mapper = mapper;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
    }

    @Transactional(readOnly = true)
    public ActivityTimelinePageResponse getTimeline(
            UUID workspaceId,
            Set<ActivityAction> actions,
            Set<ActivityEntityType> entityTypes,
            UUID actorId,
            Instant from,
            Instant to,
            String cursor,
            int size,
            UUID userId) {
        requireActiveMember(workspaceId, userId);
        ActivityCursor decodedCursor = cursor == null || cursor.isBlank() ? null : cursorCodec.decode(cursor);
        ActivityTimelineQuery query = new ActivityTimelineQuery(
                workspaceId, actions, entityTypes, actorId, from, to, decodedCursor, size);

        List<ActivityCandidate> sorted = readers.stream()
                .flatMap(reader -> reader.read(query, query.size() + 1).stream())
                .filter(candidate -> workspaceId.equals(candidate.workspaceId()))
                .filter(candidate -> query.actions().isEmpty() || query.actions().contains(candidate.action()))
                .filter(candidate -> query.entityTypes().isEmpty() || query.entityTypes().contains(candidate.entityType()))
                .filter(candidate -> query.actorId() == null || query.actorId().equals(candidate.actor().id()))
                .filter(candidate -> query.from() == null || !candidate.occurredAt().isBefore(query.from()))
                .filter(candidate -> query.to() == null || candidate.occurredAt().isBefore(query.to()))
                .filter(candidate -> ActivityTimelineOrdering.isAfterCursor(candidate, decodedCursor))
                .sorted(ActivityCandidate.ORDERING)
                .collect(LinkedHashMap<String, ActivityCandidate>::new,
                        (byId, candidate) -> byId.putIfAbsent(candidate.activityId(), candidate),
                        Map::putAll)
                .values()
                .stream()
                .toList();

        boolean hasMore = sorted.size() > query.size();
        List<ActivityCandidate> page = sorted.stream().limit(query.size()).toList();
        String nextCursor = hasMore && !page.isEmpty() ? cursorCodec.encode(toCursor(page.getLast())) : null;

        return ActivityTimelinePageResponse.builder()
                .items(page.stream().map(mapper::toResponse).toList())
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .size(query.size())
                .build();
    }

    private ActivityCursor toCursor(ActivityCandidate candidate) {
        return new ActivityCursor(candidate.occurredAt(), candidate.source(), candidate.activityId());
    }

    private void requireActiveMember(UUID workspaceId, UUID userId) {
        workspaceRepository.findById(workspaceId)
                .filter(workspace -> workspace.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException("WORKSPACE_NOT_FOUND", "Workspace not found", HttpStatus.NOT_FOUND));
        workspaceMemberRepository.findByWorkspaceIdAndUserIdAndMemberStatus(workspaceId, userId, "ACTIVE")
                .orElseThrow(() -> new BusinessException("WORKSPACE_ACCESS_DENIED", "Workspace access denied", HttpStatus.FORBIDDEN));
    }
}
