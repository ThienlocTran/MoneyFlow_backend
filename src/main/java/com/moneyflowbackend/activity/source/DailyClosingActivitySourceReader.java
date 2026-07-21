package com.moneyflowbackend.activity.source;

import com.moneyflowbackend.activity.domain.ActivityAction;
import com.moneyflowbackend.activity.domain.ActivityEntityType;
import com.moneyflowbackend.activity.domain.ActivitySource;
import com.moneyflowbackend.activity.domain.NavigationTargetType;
import com.moneyflowbackend.activity.dto.ActivityActorSummary;
import com.moneyflowbackend.activity.dto.ActivityNavigationTarget;
import com.moneyflowbackend.activity.internal.ActivityCandidate;
import com.moneyflowbackend.activity.internal.ActivitySafeDetails;
import com.moneyflowbackend.activity.internal.ActivityTimelineOrdering;
import com.moneyflowbackend.activity.query.ActivityCursor;
import com.moneyflowbackend.activity.query.ActivityIdFactory;
import com.moneyflowbackend.activity.query.ActivityTimelineQuery;
import com.moneyflowbackend.closing.repository.DailyClosingRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DailyClosingActivitySourceReader implements ActivitySourceReader {
    private static final ActivitySource SOURCE = ActivitySource.DAILY_CLOSING;

    private final DailyClosingRepository dailyClosingRepository;

    public DailyClosingActivitySourceReader(DailyClosingRepository dailyClosingRepository) {
        this.dailyClosingRepository = dailyClosingRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivityCandidate> read(ActivityTimelineQuery query, int limit) {
        if (!query.actions().isEmpty() && !query.actions().contains(ActivityAction.DAILY_CLOSING_COMPLETED)) {
            return List.of();
        }
        if (!query.entityTypes().isEmpty() && !query.entityTypes().contains(ActivityEntityType.DAILY_CLOSING)) {
            return List.of();
        }

        ActivityCursor cursor = query.cursor();
        UUID cursorId = cursor != null && cursor.source() == SOURCE
                ? UUID.fromString(cursor.stableId().substring((SOURCE.name() + ":").length()))
                : null;

        return dailyClosingRepository.findActivityContextPage(
                        query.workspaceId(),
                        query.from(),
                        query.to(),
                        query.actorId(),
                        cursor == null ? null : cursor.occurredAt(),
                        cursor == null ? null : cursor.sourceRank(),
                        cursorId,
                        SOURCE.rank(),
                        PageRequest.of(0, Math.max(limit, 1)))
                .stream()
                .map(this::map)
                .filter(candidate -> ActivityTimelineOrdering.isAfterCursor(candidate, query.cursor()))
                .sorted(ActivityCandidate.ORDERING)
                .limit(limit)
                .toList();
    }

    private ActivityCandidate map(DailyClosingRepository.ActivityDailyClosingContext closing) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("closingDate", closing.getClosingDate());
        details.put("snapshotCount", closing.getSnapshotCount());

        return new ActivityCandidate(
                ActivityIdFactory.stableId(SOURCE, closing.getId()),
                closing.getWorkspaceId(),
                closing.getCompletedAt(),
                SOURCE.rank(),
                SOURCE,
                actor(closing),
                ActivityAction.DAILY_CLOSING_COMPLETED,
                ActivityEntityType.DAILY_CLOSING,
                closing.getId(),
                null,
                null,
                closing.getClosingDate(),
                new ActivityNavigationTarget(NavigationTargetType.DAILY_CLOSING, closing.getId()),
                ActivitySafeDetails.whitelist(details));
    }

    private ActivityActorSummary actor(DailyClosingRepository.ActivityDailyClosingContext closing) {
        return closing.getCompletedByUserId() == null
                ? ActivityActorSummary.unknown()
                : ActivityActorSummary.user(closing.getCompletedByUserId(), closing.getCompletedByDisplayName());
    }
}
