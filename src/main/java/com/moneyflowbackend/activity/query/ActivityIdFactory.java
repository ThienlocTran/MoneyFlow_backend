package com.moneyflowbackend.activity.query;

import com.moneyflowbackend.activity.domain.ActivitySource;

import java.util.UUID;

public final class ActivityIdFactory {
    private ActivityIdFactory() {
    }

    public static String stableId(ActivitySource source, UUID sourceRecordId) {
        if (source == null) {
            throw new IllegalArgumentException("source is required");
        }
        if (sourceRecordId == null) {
            throw new IllegalArgumentException("sourceRecordId is required");
        }
        return source.name() + ":" + sourceRecordId;
    }
}
