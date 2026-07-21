package com.moneyflowbackend.activity.internal;

import com.moneyflowbackend.activity.domain.ActivityAction;
import com.moneyflowbackend.activity.domain.ActivityEntityType;
import com.moneyflowbackend.activity.domain.ActivitySource;
import com.moneyflowbackend.activity.dto.ActivityActorSummary;
import com.moneyflowbackend.activity.dto.ActivityNavigationTarget;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record ActivityCandidate(
        String activityId,
        UUID workspaceId,
        Instant occurredAt,
        int sourceRank,
        ActivitySource source,
        ActivityActorSummary actor,
        ActivityAction action,
        ActivityEntityType entityType,
        UUID entityId,
        BigDecimal amount,
        String direction,
        LocalDate businessDate,
        ActivityNavigationTarget navigationTarget,
        Map<String, Object> details
) {
    public static final Comparator<ActivityCandidate> ORDERING = Comparator
            .comparing(ActivityCandidate::occurredAt).reversed()
            .thenComparing(ActivityCandidate::sourceRank, Comparator.reverseOrder())
            .thenComparing(ActivityCandidate::activityId, Comparator.reverseOrder());

    public ActivityCandidate {
        Objects.requireNonNull(activityId, "activityId is required");
        Objects.requireNonNull(workspaceId, "workspaceId is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        Objects.requireNonNull(source, "source is required");
        Objects.requireNonNull(actor, "actor is required");
        Objects.requireNonNull(action, "action is required");
        Objects.requireNonNull(entityType, "entityType is required");
        navigationTarget = navigationTarget == null ? ActivityNavigationTarget.none() : navigationTarget;
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
