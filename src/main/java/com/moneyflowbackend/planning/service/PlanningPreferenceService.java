package com.moneyflowbackend.planning.service;

import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.planning.dto.PlanningPreferenceRequest;
import com.moneyflowbackend.planning.dto.PlanningPreferenceResponse;
import com.moneyflowbackend.planning.model.PlanningHorizon;
import com.moneyflowbackend.planning.model.PlanningPreference;
import com.moneyflowbackend.planning.repository.PlanningPreferenceRepository;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.repository.WalletRepository;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
public class PlanningPreferenceService {
    private final PlanningPreferenceRepository preferenceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WalletRepository walletRepository;

    public PlanningPreferenceService(
            PlanningPreferenceRepository preferenceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            WalletRepository walletRepository) {
        this.preferenceRepository = preferenceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.walletRepository = walletRepository;
    }

    @Transactional(readOnly = true)
    public PlanningPreferenceResponse get(UUID workspaceId, UUID userId) {
        requireReadableMember(workspaceId, userId);
        return map(preferenceRepository.findByWorkspaceId(workspaceId).orElseGet(() -> defaults(workspaceId)));
    }

    @Transactional
    public PlanningPreferenceResponse put(UUID workspaceId, PlanningPreferenceRequest req, UUID userId) {
        requireWritableMember(workspaceId, userId);
        PlanningPreference preference = preferenceRepository.findByWorkspaceIdForUpdate(workspaceId)
                .orElseGet(() -> PlanningPreference.builder()
                        .workspace(requireReadableMember(workspaceId, userId).getWorkspace())
                        .defaultHorizon(PlanningHorizon.CURRENT_MONTH.name())
                        .useIncludedWallets(true)
                        .selectedWalletIds(new LinkedHashSet<>())
                        .build());
        if (req != null && req.version() != null && preference.getVersion() != null && !req.version().equals(preference.getVersion())) {
            throw new BusinessException("OPTIMISTIC_LOCK_CONFLICT", "Planning preferences were updated by another request", HttpStatus.CONFLICT);
        }
        PlanningHorizon horizon = req == null || req.defaultHorizon() == null || req.defaultHorizon().isBlank()
                ? PlanningHorizon.CURRENT_MONTH
                : parseHorizon(req.defaultHorizon());
        if (horizon == PlanningHorizon.CUSTOM && (req == null || req.customFrom() == null || req.customTo() == null)) {
            throw new BusinessException("INVALID_PLANNING_HORIZON", "CUSTOM horizon requires from and to");
        }
        if (horizon == PlanningHorizon.CUSTOM && req.customTo().isBefore(req.customFrom())) {
            throw new BusinessException("INVALID_PLANNING_HORIZON", "CUSTOM horizon to must be on or after from");
        }
        boolean useIncludedWallets = req == null || req.useIncludedWallets() == null || req.useIncludedWallets();
        LinkedHashSet<UUID> walletIds = new LinkedHashSet<>(req == null || req.selectedWalletIds() == null ? List.of() : req.selectedWalletIds());
        if (!walletIds.isEmpty()) {
            List<Wallet> wallets = walletRepository.findAllByWorkspaceIdAndIdInAndIsActiveTrue(workspaceId, walletIds.stream().toList());
            if (wallets.size() != walletIds.size()) {
                throw new BusinessException("WALLET_NOT_FOUND", "One or more wallets are missing or inaccessible", HttpStatus.NOT_FOUND);
            }
        }
        preference.setDefaultHorizon(horizon.name());
        preference.setCustomFrom(horizon == PlanningHorizon.CUSTOM ? req.customFrom() : null);
        preference.setCustomTo(horizon == PlanningHorizon.CUSTOM ? req.customTo() : null);
        preference.setUseIncludedWallets(useIncludedWallets);
        preference.setSelectedWalletIds(walletIds);
        return map(preferenceRepository.saveAndFlush(preference));
    }

    private WorkspaceMember requireReadableMember(UUID workspaceId, UUID userId) {
        return workspaceMemberRepository.findByWorkspaceIdAndUserIdAndMemberStatus(workspaceId, userId, "ACTIVE")
                .orElseThrow(() -> new BusinessException("FORBIDDEN", "You cannot access this workspace", HttpStatus.FORBIDDEN));
    }

    private WorkspaceMember requireWritableMember(UUID workspaceId, UUID userId) {
        WorkspaceMember member = requireReadableMember(workspaceId, userId);
        if (member.getRole() == WorkspaceRole.VIEWER) {
            throw new BusinessException("FORBIDDEN", "You cannot modify planning preferences", HttpStatus.FORBIDDEN);
        }
        return member;
    }

    private PlanningHorizon parseHorizon(String raw) {
        try {
            return PlanningHorizon.valueOf(raw.trim().toUpperCase());
        } catch (Exception ex) {
            throw new BusinessException("INVALID_PLANNING_HORIZON", "Planning horizon is invalid");
        }
    }

    private PlanningPreference defaults(UUID workspaceId) {
        return PlanningPreference.builder()
                .workspace(com.moneyflowbackend.workspace.model.Workspace.builder().id(workspaceId).build())
                .defaultHorizon(PlanningHorizon.CURRENT_MONTH.name())
                .useIncludedWallets(true)
                .selectedWalletIds(new LinkedHashSet<>())
                .build();
    }

    private PlanningPreferenceResponse map(PlanningPreference preference) {
        return new PlanningPreferenceResponse(
                preference.getWorkspace().getId(),
                PlanningHorizon.valueOf(preference.getDefaultHorizon()),
                preference.getCustomFrom(),
                preference.getCustomTo(),
                preference.isUseIncludedWallets(),
                List.copyOf(preference.getSelectedWalletIds()),
                preference.getCreatedAt(),
                preference.getUpdatedAt(),
                preference.getVersion());
    }
}
