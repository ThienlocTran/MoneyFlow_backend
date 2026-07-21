package com.moneyflowbackend.activity.dto;

import com.moneyflowbackend.activity.domain.NavigationTargetType;

import java.util.UUID;

public record ActivityNavigationTarget(
        NavigationTargetType type,
        UUID entityId
) {
    public ActivityNavigationTarget {
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        if (type == NavigationTargetType.NONE) {
            entityId = null;
        }
    }

    public static ActivityNavigationTarget none() {
        return new ActivityNavigationTarget(NavigationTargetType.NONE, null);
    }
}
