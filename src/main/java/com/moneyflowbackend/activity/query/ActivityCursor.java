package com.moneyflowbackend.activity.query;

import com.moneyflowbackend.activity.domain.ActivitySource;

import java.time.Instant;

public record ActivityCursor(
        Instant occurredAt,
        ActivitySource source,
        String stableId
) {
    public ActivityCursor {
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt is required");
        }
        if (source == null) {
            throw new IllegalArgumentException("source is required");
        }
        if (stableId == null || stableId.isBlank()) {
            throw new IllegalArgumentException("stableId is required");
        }
    }

    public int sourceRank() {
        return source.rank();
    }
}
