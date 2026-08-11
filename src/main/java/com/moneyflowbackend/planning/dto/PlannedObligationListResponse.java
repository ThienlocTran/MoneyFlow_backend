package com.moneyflowbackend.planning.dto;

import java.util.List;

public record PlannedObligationListResponse(
        List<PlannedObligationResponse> obligations,
        int count,
        int limit
) {
}
