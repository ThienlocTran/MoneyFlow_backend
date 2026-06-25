package com.moneyflowbackend.debt.controller;

import com.moneyflowbackend.debt.dto.DebtResponse;
import com.moneyflowbackend.dto.ApiResponse;
import com.moneyflowbackend.workspace.service.WorkspaceService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/debts")
public class DebtController {
    private final WorkspaceService workspaceService;

    @PersistenceContext
    private EntityManager entityManager;

    public DebtController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<DebtResponse>>> list(@PathVariable UUID workspaceId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = UUID.fromString(auth.getName());
        workspaceService.verifyMembership(workspaceId, userId);

        List<Tuple> rows = entityManager.createNativeQuery("""
                SELECT d.id,
                       p.display_name AS counterparty_name,
                       d.direction,
                       d.principal_amount,
                       COALESCE(SUM(dp.amount), 0) AS paid_amount,
                       GREATEST(d.principal_amount - COALESCE(SUM(dp.amount), 0), 0) AS remaining_amount,
                       d.opened_on,
                       d.due_on,
                       d.closed_on,
                       d.debt_status,
                       d.note,
                       COUNT(dp.id) AS payment_count
                FROM debts d
                JOIN workspace_people p ON p.id = d.counterparty_person_id
                LEFT JOIN debt_payments dp ON dp.debt_id = d.id
                WHERE d.workspace_id = :workspaceId
                GROUP BY d.id, p.display_name
                ORDER BY d.opened_on DESC, d.created_at DESC
                """, Tuple.class)
                .setParameter("workspaceId", workspaceId)
                .getResultList();

        List<DebtResponse> res = rows.stream().map(row -> DebtResponse.builder()
                .id((UUID) row.get("id"))
                .counterpartyName((String) row.get("counterparty_name"))
                .direction((String) row.get("direction"))
                .principalAmount((BigDecimal) row.get("principal_amount"))
                .paidAmount((BigDecimal) row.get("paid_amount"))
                .remainingAmount((BigDecimal) row.get("remaining_amount"))
                .openedOn(toLocalDate(row.get("opened_on")))
                .dueOn(toLocalDate(row.get("due_on")))
                .closedOn(toLocalDate(row.get("closed_on")))
                .status((String) row.get("debt_status"))
                .note((String) row.get("note"))
                .paymentCount(((Number) row.get("payment_count")).longValue())
                .build()).toList();

        return ResponseEntity.ok(ApiResponse.ok("Debts loaded", res));
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate localDate) return localDate;
        if (value instanceof Date date) return date.toLocalDate();
        return LocalDate.parse(value.toString());
    }
}
