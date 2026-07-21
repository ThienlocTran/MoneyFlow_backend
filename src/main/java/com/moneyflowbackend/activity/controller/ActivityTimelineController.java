package com.moneyflowbackend.activity.controller;

import com.moneyflowbackend.activity.domain.ActivityAction;
import com.moneyflowbackend.activity.domain.ActivityEntityType;
import com.moneyflowbackend.activity.dto.ActivityTimelinePageResponse;
import com.moneyflowbackend.activity.query.ActivityTimelineQuery;
import com.moneyflowbackend.activity.service.ActivityTimelineService;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/activity-timeline")
public class ActivityTimelineController {
    private final ActivityTimelineService activityTimelineService;

    public ActivityTimelineController(ActivityTimelineService activityTimelineService) {
        this.activityTimelineService = activityTimelineService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ActivityTimelinePageResponse>> getTimeline(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String actions,
            @RequestParam(required = false) String entityTypes,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        ActivityTimelinePageResponse response = activityTimelineService.getTimeline(
                workspaceId,
                parseEnumSet(actions, ActivityAction.class),
                parseEnumSet(entityTypes, ActivityEntityType.class),
                parseUuid(actorId),
                parseInstant(from),
                parseInstant(to),
                cursor,
                parseSize(size),
                currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Activity timeline loaded", response));
    }

    private <E extends Enum<E>> Set<E> parseEnumSet(String value, Class<E> enumType) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        try {
            return Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(token -> !token.isEmpty())
                    .map(token -> Enum.valueOf(enumType, token))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("INVALID_ACTIVITY_FILTER", "Invalid activity filter");
        }
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new BusinessException("INVALID_ACTIVITY_DATE_RANGE", "Invalid activity date range");
        }
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("INVALID_ACTIVITY_FILTER", "Invalid activity filter");
        }
    }

    private int parseSize(String value) {
        if (value == null || value.isBlank()) {
            return ActivityTimelineQuery.DEFAULT_SIZE;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException("INVALID_ACTIVITY_PAGE_SIZE", "Activity page size must be between 1 and 100");
        }
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }
}
