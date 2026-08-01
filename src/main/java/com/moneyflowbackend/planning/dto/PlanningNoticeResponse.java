package com.moneyflowbackend.planning.dto;

public record PlanningNoticeResponse(
        String code,
        String message,
        String severity) {
}
