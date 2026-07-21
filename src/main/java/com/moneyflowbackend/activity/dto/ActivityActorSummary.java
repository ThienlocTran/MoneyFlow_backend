package com.moneyflowbackend.activity.dto;

import com.moneyflowbackend.activity.domain.ActivityActorType;

import java.util.UUID;

public record ActivityActorSummary(
        ActivityActorType type,
        UUID id,
        String displayName
) {
    public ActivityActorSummary {
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        if (type != ActivityActorType.USER && id != null) {
            throw new IllegalArgumentException("Only USER actors can carry an id");
        }
    }

    public static ActivityActorSummary user(UUID id, String displayName) {
        if (id == null) {
            return unknown();
        }
        return new ActivityActorSummary(ActivityActorType.USER, id, displayName);
    }

    public static ActivityActorSummary system() {
        return new ActivityActorSummary(ActivityActorType.SYSTEM, null, null);
    }

    public static ActivityActorSummary unknown() {
        return new ActivityActorSummary(ActivityActorType.UNKNOWN, null, null);
    }
}
