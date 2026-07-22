package com.moneyflowbackend.emergencyfund.service;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.emergencyfund.dto.EmergencyFundLedgerEntryResponse;
import com.moneyflowbackend.emergencyfund.dto.EmergencyFundLedgerPageResponse;
import com.moneyflowbackend.emergencyfund.dto.EmergencyFundLedgerRequest;
import com.moneyflowbackend.emergencyfund.dto.EmergencyFundPlanRequest;
import com.moneyflowbackend.emergencyfund.dto.EmergencyFundPlanResponse;
import com.moneyflowbackend.emergencyfund.model.EmergencyFundLedgerEntry;
import com.moneyflowbackend.emergencyfund.model.EmergencyFundLedgerType;
import com.moneyflowbackend.emergencyfund.model.EmergencyFundBasisMode;
import com.moneyflowbackend.emergencyfund.model.EmergencyFundPlan;
import com.moneyflowbackend.emergencyfund.model.EmergencyFundPlanStatus;
import com.moneyflowbackend.emergencyfund.repository.EmergencyFundLedgerEntryRepository;
import com.moneyflowbackend.emergencyfund.repository.EmergencyFundPlanRepository;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class EmergencyFundService {
    private static final int MAX_NOTE_LENGTH = 500;

    private final EmergencyFundPlanRepository planRepository;
    private final EmergencyFundLedgerEntryRepository ledgerRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public EmergencyFundService(
            EmergencyFundPlanRepository planRepository,
            EmergencyFundLedgerEntryRepository ledgerRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            UserRepository userRepository,
            Clock clock) {
        this.planRepository = planRepository;
        this.ledgerRepository = ledgerRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public EmergencyFundPlanResponse get(UUID workspaceId, UUID userId) {
        requireActiveMember(workspaceId, userId);
        EmergencyFundPlan plan = planRepository.findByWorkspaceId(workspaceId)
                .orElseThrow(() -> notFound());
        return map(plan, reservedAmount(workspaceId, plan));
    }

    @Transactional
    public EmergencyFundPlanResponse put(UUID workspaceId, EmergencyFundPlanRequest req, UUID userId) {
        WorkspaceMember member = requireWritableMember(workspaceId, userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));
        ValidatedPlan validated = validatePlan(req);
        EmergencyFundPlan plan = planRepository.findByWorkspaceIdForUpdate(workspaceId)
                .orElseGet(() -> EmergencyFundPlan.builder()
                        .workspace(member.getWorkspace())
                        .createdByUser(user)
                        .planStatus(EmergencyFundPlanStatus.ACTIVE)
                        .build());
        plan.setTargetMonths(validated.targetMonths());
        plan.setBasisMode(EmergencyFundBasisMode.MANUAL);
        plan.setManualMonthlyExpense(validated.manualMonthlyExpense());
        plan.setUpdatedAt(Instant.now(clock));
        return map(planRepository.saveAndFlush(plan), reservedAmount(workspaceId, plan));
    }

    @Transactional
    public EmergencyFundPlanResponse updateStatus(UUID workspaceId, EmergencyFundPlanStatus status, UUID userId) {
        requireWritableMember(workspaceId, userId);
        if (status == null) {
            throw new BusinessException("INVALID_EMERGENCY_FUND_PLAN_STATUS", "Emergency fund plan status is required");
        }
        EmergencyFundPlan plan = planRepository.findByWorkspaceIdForUpdate(workspaceId)
                .orElseThrow(() -> notFound());
        plan.setPlanStatus(status);
        plan.setUpdatedAt(Instant.now(clock));
        return map(planRepository.saveAndFlush(plan), reservedAmount(workspaceId, plan));
    }

    @Transactional(readOnly = true)
    public EmergencyFundLedgerPageResponse ledger(UUID workspaceId, int page, int size, UUID userId) {
        requireActiveMember(workspaceId, userId);
        EmergencyFundPlan plan = planRepository.findByWorkspaceId(workspaceId)
                .orElseThrow(() -> notFound());
        int pageNumber = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), 100);
        Page<EmergencyFundLedgerEntry> result = ledgerRepository.findAllByWorkspaceIdAndEmergencyFundPlanId(
                workspaceId,
                plan.getId(),
                PageRequest.of(pageNumber, pageSize, Sort.by(
                        Sort.Order.desc("occurredAt"),
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id"))));
        return EmergencyFundLedgerPageResponse.builder()
                .content(result.getContent().stream().map(this::mapLedgerEntry).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .first(result.isFirst())
                .last(result.isLast())
                .build();
    }

    @Transactional
    public EmergencyFundLedgerEntryResponse allocate(UUID workspaceId, EmergencyFundLedgerRequest req, UUID userId) {
        return addLedgerEntry(workspaceId, req, userId, EmergencyFundLedgerType.ALLOCATE);
    }

    @Transactional
    public EmergencyFundLedgerEntryResponse release(UUID workspaceId, EmergencyFundLedgerRequest req, UUID userId) {
        return addLedgerEntry(workspaceId, req, userId, EmergencyFundLedgerType.RELEASE);
    }

    private EmergencyFundLedgerEntryResponse addLedgerEntry(
            UUID workspaceId,
            EmergencyFundLedgerRequest req,
            UUID userId,
            EmergencyFundLedgerType type) {
        WorkspaceMember member = requireWritableMember(workspaceId, userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));
        EmergencyFundPlan plan = planRepository.findByWorkspaceIdForUpdate(workspaceId)
                .orElseThrow(() -> notFound());
        BigDecimal amount = validateLedgerAmount(req == null ? null : req.getAmount());
        BigDecimal delta = type == EmergencyFundLedgerType.RELEASE ? amount.negate() : amount;
        BigDecimal reserved = reservedAmount(workspaceId, plan);
        if (reserved.add(delta).compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("EMERGENCY_FUND_RESERVED_NEGATIVE", "Emergency fund reserved amount cannot be negative", HttpStatus.CONFLICT);
        }
        plan.setUpdatedAt(Instant.now(clock));
        EmergencyFundLedgerEntry entry = EmergencyFundLedgerEntry.builder()
                .workspace(member.getWorkspace())
                .emergencyFundPlan(plan)
                .entryType(type)
                .amountDelta(delta)
                .note(validateNote(req == null ? null : req.getNote()))
                .actorUser(user)
                .occurredAt(req == null || req.getOccurredAt() == null ? Instant.now(clock) : req.getOccurredAt())
                .build();
        return mapLedgerEntry(ledgerRepository.saveAndFlush(entry));
    }

    private ValidatedPlan validatePlan(EmergencyFundPlanRequest req) {
        if (req == null) {
            throw new BusinessException("VALIDATION_ERROR", "Request body is required");
        }
        if (req.getBasisMode() != null && req.getBasisMode() != EmergencyFundBasisMode.MANUAL) {
            throw new BusinessException("INVALID_EMERGENCY_FUND_BASIS_MODE", "Emergency fund basis mode must be MANUAL");
        }
        if (req.getTargetMonths() == null || req.getTargetMonths() <= 0) {
            throw new BusinessException("INVALID_EMERGENCY_FUND_TARGET_MONTHS", "Emergency fund target months must be positive");
        }
        BigDecimal manualMonthlyExpense = req.getManualMonthlyExpense();
        if (manualMonthlyExpense == null || manualMonthlyExpense.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_EMERGENCY_FUND_MANUAL_MONTHLY_EXPENSE", "Emergency fund manual monthly expense must be positive");
        }
        return new ValidatedPlan(req.getTargetMonths(), manualMonthlyExpense);
    }

    private BigDecimal validateLedgerAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_EMERGENCY_FUND_LEDGER_AMOUNT", "Emergency fund ledger amount must be positive");
        }
        return amount;
    }

    private String validateNote(String note) {
        if (note == null) {
            return null;
        }
        String value = note.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > MAX_NOTE_LENGTH) {
            throw new BusinessException("VALIDATION_ERROR", "Emergency fund ledger note is too long");
        }
        return value;
    }

    private WorkspaceMember requireActiveMember(UUID workspaceId, UUID userId) {
        workspaceRepository.findById(workspaceId)
                .filter(ws -> ws.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException("WORKSPACE_NOT_FOUND", "Workspace not found", HttpStatus.NOT_FOUND));
        return workspaceMemberRepository.findByWorkspaceIdAndUserIdAndMemberStatus(workspaceId, userId, "ACTIVE")
                .orElseThrow(() -> new BusinessException("WORKSPACE_ACCESS_DENIED", "Workspace access denied", HttpStatus.FORBIDDEN));
    }

    private WorkspaceMember requireWritableMember(UUID workspaceId, UUID userId) {
        WorkspaceMember member = requireActiveMember(workspaceId, userId);
        if (member.getRole() == WorkspaceRole.VIEWER) {
            throw new BusinessException("FORBIDDEN", "Viewer cannot modify emergency fund", HttpStatus.FORBIDDEN);
        }
        return member;
    }

    private BusinessException notFound() {
        return new BusinessException("EMERGENCY_FUND_PLAN_NOT_FOUND", "Emergency fund plan not found", HttpStatus.NOT_FOUND);
    }

    private BigDecimal reservedAmount(UUID workspaceId, EmergencyFundPlan plan) {
        return plan.getId() == null ? BigDecimal.ZERO : ledgerRepository.sumReservedAmount(workspaceId, plan.getId());
    }

    private EmergencyFundPlanResponse map(EmergencyFundPlan plan, BigDecimal reservedAmount) {
        return EmergencyFundPlanResponse.builder()
                .id(plan.getId())
                .workspaceId(plan.getWorkspace().getId())
                .targetMonths(plan.getTargetMonths())
                .basisMode(plan.getBasisMode())
                .manualMonthlyExpense(plan.getManualMonthlyExpense())
                .planStatus(plan.getPlanStatus())
                .reservedAmount(reservedAmount)
                .createdByUserId(plan.getCreatedByUser().getId())
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .version(plan.getVersion())
                .build();
    }

    private EmergencyFundLedgerEntryResponse mapLedgerEntry(EmergencyFundLedgerEntry entry) {
        return EmergencyFundLedgerEntryResponse.builder()
                .id(entry.getId())
                .workspaceId(entry.getWorkspace().getId())
                .emergencyFundPlanId(entry.getEmergencyFundPlan().getId())
                .entryType(entry.getEntryType())
                .amountDelta(entry.getAmountDelta())
                .note(entry.getNote())
                .actorUserId(entry.getActorUser().getId())
                .occurredAt(entry.getOccurredAt())
                .createdAt(entry.getCreatedAt())
                .version(entry.getVersion())
                .build();
    }

    private record ValidatedPlan(Integer targetMonths, BigDecimal manualMonthlyExpense) {
    }
}
