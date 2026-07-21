package com.moneyflowbackend.activity.internal;

import com.moneyflowbackend.activity.query.ActivityCursor;

public final class ActivityTimelineOrdering {
    private ActivityTimelineOrdering() {
    }

    public static boolean isAfterCursor(ActivityCandidate candidate, ActivityCursor cursor) {
        if (cursor == null) {
            return true;
        }
        int occurred = candidate.occurredAt().compareTo(cursor.occurredAt());
        if (occurred != 0) {
            return occurred < 0;
        }
        int rank = Integer.compare(candidate.sourceRank(), cursor.sourceRank());
        if (rank != 0) {
            return rank < 0;
        }
        return candidate.activityId().compareTo(cursor.stableId()) < 0;
    }
}
