package com.moneyflowbackend.categoryboard.dto;

import java.util.List;

public record UncategorizedGroupResponse(
        String groupKey,
        String name,
        long categoryCount,
        UncategorizedStatsResponse stats,
        List<CategoryBoardItemResponse> categories) {
}
