package com.moneyflowbackend.activity.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ActivityTimelinePageResponse {
    private List<ActivityTimelineItemResponse> items;
    private String nextCursor;
    private boolean hasMore;
    private int size;
}
