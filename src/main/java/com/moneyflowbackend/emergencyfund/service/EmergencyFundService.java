package com.moneyflowbackend.emergencyfund.service;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.emergencyfund.dto.EmergencyFundPlanRequest;
import com.moneyflowbackend.emergencyfund.dto.EmergencyFundPlanResponse;
import com.moneyflowbackend.emergencyfund.model.EmergencyFundBasisMode;
import com.moneyflowbackend.emergencyfund.model.EmergencyFundPlan;
import com.moneyflowbackend.emergencyfund.model.EmergencyFundPlanStatus;
import com.moneyflowbackend.emergencyfund.repository.EmergencyFundPlanRepository;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class EmergencyFundService {
    private final EmergencyFundPlanRepository planRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public EmergencyFundService(
            EmergencyFundPlanRepository planRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            UserRepository userRepository,
            Clock clock) {
        this.planRepository = planRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public EmergencyFundPlanResponse get(UUID workspaceId, UUID userId) {
        requireActiveMember(workspaceId, userId);
        return map(planRepository.findByWorkspaceId(workspaceId)
                .orElseThrow(() -> notFound()));
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
        return map(planRepository.saveAndFlush(plan));
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
        return map(planRepository.saveAndFlush(plan));
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

    private EmergencyFundPlanResponse map(EmergencyFundPlan plan) {
        return EmergencyFundPlanResponse.builder()
                .id(plan.getId())
                .workspaceId(plan.getWorkspace().getId())
                .targetMonths(plan.getTargetMonths())
                .basisMode(plan.getBasisMode())
                .manualMonthlyExpense(plan.getManualMonthlyExpense())
                .planStatus(plan.getPlanStatus())
                .createdByUserId(plan.getCreatedByUser().getId())
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .version(plan.getVersion())
                .build();
    }

    private record ValidatedPlan(Integer targetMonths, BigDecimal manualMonthlyExpense) {
    }
}
