package com.moneyflowbackend.obligation.controller;

import com.moneyflowbackend.dto.ApiResponse;
import com.moneyflowbackend.obligation.dto.FinancialInboxGroup;
import com.moneyflowbackend.obligation.dto.FinancialInboxResponse;
import com.moneyflowbackend.obligation.model.ObligationDirection;
import com.moneyflowbackend.obligation.service.FinancialInboxService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/financial-inbox")
public class FinancialInboxController {
    private final FinancialInboxService inboxService;

    public FinancialInboxController(FinancialInboxService inboxService) {
        this.inboxService = inboxService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<FinancialInboxResponse>> inbox(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) FinancialInboxGroup group,
            @RequestParam(required = false) ObligationDirection direction,
            @RequestParam(required = false) UUID templateId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        FinancialInboxResponse response = inboxService.inbox(
                workspaceId,
                group,
                direction,
                templateId,
                from,
                to,
                page,
                size,
                currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Financial inbox loaded", response));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }
}
