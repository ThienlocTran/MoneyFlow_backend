package com.moneyflowbackend.activity.source;

import com.moneyflowbackend.activity.internal.ActivityCandidate;
import com.moneyflowbackend.activity.query.ActivityTimelineQuery;

import java.util.List;

public interface ActivitySourceReader {
    List<ActivityCandidate> read(ActivityTimelineQuery query, int limit);
}
