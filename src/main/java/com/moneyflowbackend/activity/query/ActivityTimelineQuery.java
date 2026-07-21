package com.moneyflowbackend.activity.query;

import com.moneyflowbackend.activity.domain.ActivityAction;
import com.moneyflowbackend.activity.domain.ActivityEntityType;
import com.moneyflowbackend.common.exception.BusinessException;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record ActivityTimelineQuery(
        UUID workspaceId,
        Set<ActivityAction> actions,
        Set<ActivityEntityType> entityTypes,
        UUID actorId,
        Instant from,
        Instant to,
        ActivityCursor cursor,
        int size
) {
    public static final int DEFAULT_SIZE = 30;
    public static final int MAX_SIZE = 100;

    public ActivityTimelineQuery {
        if (workspaceId == null) {
            throw new IllegalArgumentException("workspaceId is required");
        }
        actions = actions == null ? Set.of() : Set.copyOf(actions);
        entityTypes = entityTypes == null ? Set.of() : Set.copyOf(entityTypes);
        size = size == 0 ? DEFAULT_SIZE : size;
        if (size < 1 || size > MAX_SIZE) {
            throw new BusinessException("INVALID_ACTIVITY_PAGE_SIZE", "Activity page size must be between 1 and 100");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException("INVALID_ACTIVITY_DATE_RANGE", "Activity from must be before or equal to to");
        }
    }
}
