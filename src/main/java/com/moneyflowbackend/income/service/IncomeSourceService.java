package com.moneyflowbackend.income.service;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.income.dto.IncomeSourceRequest;
import com.moneyflowbackend.income.dto.IncomeSourceResponse;
import com.moneyflowbackend.income.model.IncomeSource;
import com.moneyflowbackend.income.model.IncomeSourceStatus;
import com.moneyflowbackend.income.model.IncomeSourceType;
import com.moneyflowbackend.income.repository.IncomeSourceRepository;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class IncomeSourceService {
    private static final int MAX_NAME_LENGTH = 160;
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final String ACTIVE_NAME_INDEX = "uq_income_sources_workspace_active_name";

    private final IncomeSourceRepository incomeSourceRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;

    public IncomeSourceService(
            IncomeSourceRepository incomeSourceRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            UserRepository userRepository) {
        this.incomeSourceRepository = incomeSourceRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<IncomeSourceResponse> list(UUID workspaceId, IncomeSourceStatus status, String search, UUID userId) {
        WorkspaceMember member = requireActiveMember(workspaceId, userId);
        IncomeSourceStatus effectiveStatus = status == null ? IncomeSourceStatus.ACTIVE : status;
        String pattern = searchPattern(search);
        List<IncomeSource> sources = pattern == null
                ? incomeSourceRepository.findAllForList(workspaceId, effectiveStatus)
                : incomeSourceRepository.searchForList(workspaceId, effectiveStatus, pattern);
        return sources.stream()
                .map(source -> mapToResponse(source, member))
                .toList();
    }

    @Transactional(readOnly = true)
    public IncomeSourceResponse get(UUID workspaceId, UUID incomeSourceId, UUID userId) {
        WorkspaceMember member = requireActiveMember(workspaceId, userId);
        IncomeSource source = findInWorkspace(workspaceId, incomeSourceId);
        return mapToResponse(source, member);
    }

    @Transactional
    public IncomeSourceResponse create(UUID workspaceId, IncomeSourceRequest req, UUID userId) {
        WorkspaceMember member = requireWritableMember(workspaceId, userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));
        ValidatedRequest validated = validate(req);
        ensureUniqueActiveName(workspaceId, validated.name(), null);
        IncomeSource source = IncomeSource.builder()
                .workspace(member.getWorkspace())
                .name(validated.name())
                .type(validated.type())
                .description(validated.description())
                .status(IncomeSourceStatus.ACTIVE)
                .createdByUser(user)
                .build();
        try {
            return mapToResponse(incomeSourceRepository.saveAndFlush(source), member);
        } catch (DataIntegrityViolationException ex) {
            throw translateIntegrity(ex);
        }
    }

    @Transactional
    public IncomeSourceResponse update(UUID workspaceId, UUID incomeSourceId, IncomeSourceRequest req, UUID userId) {
        WorkspaceMember member = requireWritableMember(workspaceId, userId);
        IncomeSource source = findInWorkspaceForUpdate(workspaceId, incomeSourceId);
        if (source.getStatus() == IncomeSourceStatus.ARCHIVED) {
            throw archived();
        }
        ValidatedRequest validated = validate(req);
        ensureUniqueActiveName(workspaceId, validated.name(), incomeSourceId);
        source.setName(validated.name());
        source.setType(validated.type());
        source.setDescription(validated.description());
        source.setUpdatedAt(Instant.now());
        try {
            return mapToResponse(incomeSourceRepository.saveAndFlush(source), member);
        } catch (DataIntegrityViolationException ex) {
            throw translateIntegrity(ex);
        }
    }

    @Transactional
    public IncomeSourceResponse archive(UUID workspaceId, UUID incomeSourceId, UUID userId) {
        WorkspaceMember member = requireWritableMember(workspaceId, userId);
        IncomeSource source = findInWorkspaceForUpdate(workspaceId, incomeSourceId);
        if (source.getStatus() != IncomeSourceStatus.ARCHIVED) {
            source.setStatus(IncomeSourceStatus.ARCHIVED);
            source.setUpdatedAt(Instant.now());
        }
        return mapToResponse(incomeSourceRepository.saveAndFlush(source), member);
    }

    @Transactional
    public IncomeSourceResponse restore(UUID workspaceId, UUID incomeSourceId, UUID userId) {
        WorkspaceMember member = requireWritableMember(workspaceId, userId);
        IncomeSource source = findInWorkspaceForUpdate(workspaceId, incomeSourceId);
        if (source.getStatus() == IncomeSourceStatus.ACTIVE) {
            return mapToResponse(source, member);
        }
        ensureUniqueActiveName(workspaceId, source.getName(), incomeSourceId);
        source.setStatus(IncomeSourceStatus.ACTIVE);
        source.setUpdatedAt(Instant.now());
        try {
            return mapToResponse(incomeSourceRepository.saveAndFlush(source), member);
        } catch (DataIntegrityViolationException ex) {
            throw translateIntegrity(ex);
        }
    }

    private ValidatedRequest validate(IncomeSourceRequest req) {
        if (req == null) {
            throw new BusinessException("VALIDATION_ERROR", "Request body is required");
        }
        String name = normalizeName(req.getName());
        if (name.isBlank() || name.length() > MAX_NAME_LENGTH) {
            throw new BusinessException("VALIDATION_ERROR", "Income source name is invalid", Map.of("name", "Income source name is invalid"));
        }
        String description = normalizeText(req.getDescription());
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new BusinessException("VALIDATION_ERROR", "Income source description is too long", Map.of("description", "Income source description is too long"));
        }
        return new ValidatedRequest(name, parseType(req.getType()), description);
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim();
    }

    private String normalizeText(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        return value.isEmpty() ? null : value;
    }

    private IncomeSourceType parseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return IncomeSourceType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("INVALID_INCOME_SOURCE_TYPE", "Invalid income source type", Map.of("type", "Invalid income source type"));
        }
    }

    private String searchPattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private void ensureUniqueActiveName(UUID workspaceId, String name, UUID excludeId) {
        boolean exists = excludeId == null
                ? incomeSourceRepository.existsActiveNameInWorkspace(workspaceId, name)
                : incomeSourceRepository.existsActiveNameInWorkspaceExcluding(workspaceId, name, excludeId);
        if (exists) {
            throw duplicateName();
        }
    }

    private IncomeSource findInWorkspace(UUID workspaceId, UUID incomeSourceId) {
        return incomeSourceRepository.findByIdAndWorkspaceId(incomeSourceId, workspaceId)
                .orElseThrow(() -> notFound());
    }

    private IncomeSource findInWorkspaceForUpdate(UUID workspaceId, UUID incomeSourceId) {
        return incomeSourceRepository.findByIdAndWorkspaceIdForUpdate(incomeSourceId, workspaceId)
                .orElseThrow(() -> notFound());
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
            throw new BusinessException("FORBIDDEN", "Viewer cannot modify income sources", HttpStatus.FORBIDDEN);
        }
        return member;
    }

    private RuntimeException translateIntegrity(DataIntegrityViolationException ex) {
        if (isActiveNameConstraint(ex)) {
            return duplicateName();
        }
        return ex;
    }

    private boolean isActiveNameConstraint(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraint
                    && constraint.getConstraintName() != null
                    && constraint.getConstraintName().contains(ACTIVE_NAME_INDEX)) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.contains(ACTIVE_NAME_INDEX)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private BusinessException notFound() {
        return new BusinessException("INCOME_SOURCE_NOT_FOUND", "Income source not found", HttpStatus.NOT_FOUND);
    }

    private BusinessException duplicateName() {
        return new BusinessException("INCOME_SOURCE_NAME_ALREADY_EXISTS", "Income source name already exists", HttpStatus.CONFLICT);
    }

    private BusinessException archived() {
        return new BusinessException("INCOME_SOURCE_ARCHIVED", "Archived income source cannot be updated", HttpStatus.CONFLICT);
    }

    private IncomeSourceResponse mapToResponse(IncomeSource source, WorkspaceMember member) {
        return IncomeSourceResponse.builder()
                .id(source.getId())
                .workspaceId(member.getWorkspace().getId())
                .name(source.getName())
                .type(source.getType())
                .description(source.getDescription())
                .status(source.getStatus())
                .createdByUserId(source.getCreatedByUser().getId())
                .createdAt(source.getCreatedAt())
                .updatedAt(source.getUpdatedAt())
                .version(source.getVersion())
                .build();
    }

    private record ValidatedRequest(String name, IncomeSourceType type, String description) {
    }
}
