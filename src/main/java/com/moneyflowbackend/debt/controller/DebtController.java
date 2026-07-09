package com.moneyflowbackend.debt.controller;

import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.debt.dto.DebtPaymentRequest;
import com.moneyflowbackend.debt.dto.DebtPaymentResponse;
import com.moneyflowbackend.debt.dto.DebtPersonSummaryResponse;
import com.moneyflowbackend.debt.dto.DebtRequest;
import com.moneyflowbackend.debt.dto.DebtResponse;
import com.moneyflowbackend.debt.dto.DebtSummaryResponse;
import com.moneyflowbackend.dto.ApiResponse;
import com.moneyflowbackend.workspace.service.WorkspaceService;
import jakarta.validation.Valid;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
        workspaceService.verifyMembership(workspaceId, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Debts loaded", listDebts(workspaceId)));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ApiResponse<DebtResponse>> create(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody DebtRequest req) {
        workspaceService.requireWritableMember(workspaceId, currentUserId());
        String personName = normalizePersonName(req.getPersonName());
        String direction = normalizeDirection(req.getType());
        UUID personId = findOrCreatePerson(workspaceId, personName);
        UUID debtId = UUID.randomUUID();
        entityManager.createNativeQuery("""
                INSERT INTO debts (id, workspace_id, counterparty_person_id, direction, principal_amount, opened_on, due_on, closed_on, debt_status, note, created_at, updated_at)
                VALUES (:id, :workspaceId, :personId, :direction, :amount, :openedOn, :dueOn, :closedOn, :status, :note, NOW(), NOW())
                """)
                .setParameter("id", debtId)
                .setParameter("workspaceId", workspaceId)
                .setParameter("personId", personId)
                .setParameter("direction", direction)
                .setParameter("amount", req.getPrincipalAmount())
                .setParameter("openedOn", req.getOpenedDate())
                .setParameter("dueOn", req.getDueDate())
                .setParameter("closedOn", null)
                .setParameter("status", "OPEN")
                .setParameter("note", trimToNull(req.getNote()))
                .executeUpdate();
        return ResponseEntity.ok(ApiResponse.ok("Debt created", getDebt(workspaceId, debtId)));
    }

    @PostMapping("/{debtId}/payments")
    @Transactional
    public ResponseEntity<ApiResponse<DebtPaymentResponse>> recordPayment(
            @PathVariable UUID workspaceId,
            @PathVariable UUID debtId,
            @Valid @RequestBody DebtPaymentRequest req) {
        workspaceService.requireWritableMember(workspaceId, currentUserId());
        DebtResponse debt = getDebt(workspaceId, debtId);
        if ("CANCELLED".equals(debt.getStatus()) || "PAID".equals(debt.getStatus())) {
            throw new BusinessException("DEBT_CLOSED", "Debt is not open for payment");
        }
        if (req.getAmount().compareTo(debt.getRemainingAmount()) > 0) {
            throw new BusinessException("PAYMENT_EXCEEDS_REMAINING", "Payment amount exceeds remaining debt");
        }
        UUID paymentId = UUID.randomUUID();
        entityManager.createNativeQuery("""
                INSERT INTO debt_payments (id, debt_id, amount, payment_date, note, created_at)
                VALUES (:id, :debtId, :amount, :paymentDate, :note, NOW())
                """)
                .setParameter("id", paymentId)
                .setParameter("debtId", debtId)
                .setParameter("amount", req.getAmount())
                .setParameter("paymentDate", req.getPaymentDate())
                .setParameter("note", trimToNull(req.getNote()))
                .executeUpdate();
        BigDecimal remaining = debt.getRemainingAmount().subtract(req.getAmount());
        String status = remaining.compareTo(BigDecimal.ZERO) == 0 ? "PAID" : "PARTIAL";
        entityManager.createNativeQuery("""
                UPDATE debts
                SET debt_status = :status, closed_on = :closedOn, updated_at = NOW()
                WHERE id = :debtId AND workspace_id = :workspaceId
                """)
                .setParameter("status", status)
                .setParameter("closedOn", "PAID".equals(status) ? req.getPaymentDate() : null)
                .setParameter("debtId", debtId)
                .setParameter("workspaceId", workspaceId)
                .executeUpdate();
        return ResponseEntity.ok(ApiResponse.ok("Debt payment recorded", getPayment(paymentId)));
    }

    @GetMapping("/{debtId}/payments")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<DebtPaymentResponse>>> payments(
            @PathVariable UUID workspaceId,
            @PathVariable UUID debtId) {
        workspaceService.verifyMembership(workspaceId, currentUserId());
        getDebt(workspaceId, debtId);
        List<Tuple> rows = entityManager.createNativeQuery("""
                SELECT id, debt_id, amount, payment_date, note
                FROM debt_payments
                WHERE debt_id = :debtId
                ORDER BY payment_date DESC, created_at DESC
                """, Tuple.class)
                .setParameter("debtId", debtId)
                .getResultList();
        return ResponseEntity.ok(ApiResponse.ok("Debt payments loaded", rows.stream().map(this::mapPayment).toList()));
    }

    @GetMapping("/summary")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<DebtSummaryResponse>> summary(@PathVariable UUID workspaceId) {
        workspaceService.verifyMembership(workspaceId, currentUserId());
        List<DebtResponse> debts = listDebts(workspaceId);
        BigDecimal receivable = debts.stream()
                .filter(d -> "RECEIVABLE".equals(d.getDirection()))
                .map(DebtResponse::getRemainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal payable = debts.stream()
                .filter(d -> "PAYABLE".equals(d.getDirection()))
                .map(DebtResponse::getRemainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long openDebts = debts.stream().filter(d -> !"PAID".equals(d.getStatus()) && !"CANCELLED".equals(d.getStatus())).count();
        long payments = debts.stream().mapToLong(DebtResponse::getPaymentCount).sum();
        return ResponseEntity.ok(ApiResponse.ok("Debt summary loaded", DebtSummaryResponse.builder()
                .totalReceivableRemaining(receivable)
                .totalPayableRemaining(payable)
                .totalOpenDebts(openDebts)
                .totalPayments(payments)
                .build()));
    }

    @GetMapping("/by-person")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<DebtPersonSummaryResponse>>> byPerson(@PathVariable UUID workspaceId) {
        workspaceService.verifyMembership(workspaceId, currentUserId());
        List<DebtResponse> debts = listDebts(workspaceId);
        Map<String, List<DebtResponse>> groups = debts.stream()
                .collect(Collectors.groupingBy(DebtResponse::getCounterpartyName));
        List<DebtPersonSummaryResponse> res = groups.entrySet().stream()
                .map(entry -> personSummary(entry.getKey(), entry.getValue()))
                .sorted((a, b) -> a.getPersonName().compareToIgnoreCase(b.getPersonName()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok("Debt by-person summary loaded", res));
    }

    private List<DebtResponse> listDebts(UUID workspaceId) {
        List<Tuple> rows = entityManager.createNativeQuery("""
                SELECT d.id,
                       p.display_name AS counterparty_name,
                       d.direction,
                       d.principal_amount,
                       COALESCE(SUM(dp.amount), 0) AS paid_amount,
                       CASE
                           WHEN d.debt_status = 'CANCELLED' THEN 0
                           ELSE GREATEST(d.principal_amount - COALESCE(SUM(dp.amount), 0), 0)
                       END AS remaining_amount,
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

        return rows.stream().map(row -> DebtResponse.builder()
                .id(toUuid(row.get("id")))
                .counterpartyName((String) row.get("counterparty_name"))
                .direction((String) row.get("direction"))
                .principalAmount(toMoney(row.get("principal_amount")))
                .paidAmount(toMoney(row.get("paid_amount")))
                .remainingAmount(toMoney(row.get("remaining_amount")))
                .openedOn(toLocalDate(row.get("opened_on")))
                .dueOn(toLocalDate(row.get("due_on")))
                .closedOn(toLocalDate(row.get("closed_on")))
                .status((String) row.get("debt_status"))
                .note((String) row.get("note"))
                .paymentCount(((Number) row.get("payment_count")).longValue())
                .build()).toList();
    }

    private DebtResponse getDebt(UUID workspaceId, UUID debtId) {
        return listDebts(workspaceId).stream()
                .filter(debt -> debt.getId().equals(debtId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("DEBT_NOT_FOUND", "Debt not found", HttpStatus.NOT_FOUND));
    }

    private DebtPaymentResponse getPayment(UUID paymentId) {
        Tuple row = (Tuple) entityManager.createNativeQuery("""
                SELECT id, debt_id, amount, payment_date, note
                FROM debt_payments
                WHERE id = :id
                """, Tuple.class)
                .setParameter("id", paymentId)
                .getSingleResult();
        return mapPayment(row);
    }

    private DebtPaymentResponse mapPayment(Tuple row) {
        return DebtPaymentResponse.builder()
                .id(toUuid(row.get("id")))
                .debtId(toUuid(row.get("debt_id")))
                .amount(toMoney(row.get("amount")))
                .paymentDate(toLocalDate(row.get("payment_date")))
                .note((String) row.get("note"))
                .build();
    }

    private DebtPersonSummaryResponse personSummary(String personName, List<DebtResponse> debts) {
        BigDecimal original = debts.stream().map(DebtResponse::getPrincipalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = debts.stream().map(DebtResponse::getPaidAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = debts.stream().map(DebtResponse::getRemainingAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return DebtPersonSummaryResponse.builder()
                .personName(personName)
                .totalOriginalAmount(original)
                .totalPaid(paid)
                .totalRemaining(remaining)
                .openDebtCount(debts.stream().filter(d -> !"PAID".equals(d.getStatus()) && !"CANCELLED".equals(d.getStatus())).count())
                .paidDebtCount(debts.stream().filter(d -> "PAID".equals(d.getStatus())).count())
                .latestOpenedDate(debts.stream().map(DebtResponse::getOpenedOn).max(LocalDate::compareTo).orElse(null))
                .latestPaymentDate(latestPaymentDate(debts))
                .debts(debts)
                .build();
    }

    private LocalDate latestPaymentDate(List<DebtResponse> debts) {
        List<UUID> ids = debts.stream().map(DebtResponse::getId).toList();
        if (ids.isEmpty()) return null;
        Object value = entityManager.createNativeQuery("""
                SELECT MAX(payment_date)
                FROM debt_payments
                WHERE debt_id IN (:ids)
                """)
                .setParameter("ids", ids)
                .getSingleResult();
        return toLocalDate(value);
    }

    private UUID findOrCreatePerson(UUID workspaceId, String personName) {
        List<?> existing = entityManager.createNativeQuery("""
                SELECT id FROM workspace_people
                WHERE workspace_id = :workspaceId AND LOWER(display_name) = LOWER(:name)
                ORDER BY created_at
                """)
                .setParameter("workspaceId", workspaceId)
                .setParameter("name", personName)
                .setMaxResults(1)
                .getResultList();
        if (!existing.isEmpty()) {
            return toUuid(existing.get(0));
        }
        UUID personId = UUID.randomUUID();
        entityManager.createNativeQuery("""
                INSERT INTO workspace_people (id, workspace_id, display_name, person_kind, is_active, created_at)
                VALUES (:id, :workspaceId, :name, 'COUNTERPARTY', TRUE, NOW())
                """)
                .setParameter("id", personId)
                .setParameter("workspaceId", workspaceId)
                .setParameter("name", personName)
                .executeUpdate();
        return personId;
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }

    private String normalizePersonName(String personName) {
        String normalized = personName == null ? "" : personName.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "Person name is required");
        }
        return normalized;
    }

    private String normalizeDirection(String direction) {
        String normalized = direction == null ? "" : direction.trim().toUpperCase(Locale.ROOT);
        if (!List.of("RECEIVABLE", "PAYABLE").contains(normalized)) {
            throw new BusinessException("INVALID_DEBT_DIRECTION", "Debt type must be RECEIVABLE or PAYABLE");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate localDate) return localDate;
        if (value instanceof Date date) return date.toLocalDate();
        return LocalDate.parse(value.toString());
    }

    private UUID toUuid(Object value) {
        if (value instanceof UUID uuid) return uuid;
        if (value instanceof byte[] bytes) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            return new UUID(buffer.getLong(), buffer.getLong());
        }
        return UUID.fromString(value.toString());
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) return decimal;
        return new BigDecimal(value.toString());
    }

    private BigDecimal toMoney(Object value) {
        BigDecimal decimal = toBigDecimal(value).stripTrailingZeros();
        return decimal.scale() < 0 ? decimal.setScale(0) : decimal;
    }
}
