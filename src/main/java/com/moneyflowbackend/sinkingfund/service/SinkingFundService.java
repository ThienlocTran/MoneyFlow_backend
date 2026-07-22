package com.moneyflowbackend.sinkingfund.service;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.sinkingfund.dto.SinkingFundAllocationPageResponse;
import com.moneyflowbackend.sinkingfund.dto.SinkingFundAllocationRequest;
import com.moneyflowbackend.sinkingfund.dto.SinkingFundAllocationResponse;
import com.moneyflowbackend.sinkingfund.dto.SinkingFundPageResponse;
import com.moneyflowbackend.sinkingfund.dto.SinkingFundRequest;
import com.moneyflowbackend.sinkingfund.dto.SinkingFundResponse;
import com.moneyflowbackend.sinkingfund.model.SinkingFund;
import com.moneyflowbackend.sinkingfund.model.SinkingFundAllocation;
import com.moneyflowbackend.sinkingfund.model.SinkingFundAllocationType;
import com.moneyflowbackend.sinkingfund.model.SinkingFundStatus;
import com.moneyflowbackend.sinkingfund.repository.SinkingFundAllocationRepository;
import com.moneyflowbackend.sinkingfund.repository.SinkingFundRepository;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class SinkingFundService {
    private static final int MAX_NAME_LENGTH = 160;
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final int MAX_NOTE_LENGTH = 500;
    private static final String OPEN_NAME_INDEX = "uq_sinking_funds_workspace_open_name";

    private final SinkingFundRepository fundRepository;
    private final SinkingFundAllocationRepository allocationRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public SinkingFundService(
            SinkingFundRepository fundRepository,
            SinkingFundAllocationRepository allocationRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            UserRepository userRepository,
            Clock clock) {
        this.fundRepository = fundRepository;
        this.allocationRepository = allocationRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SinkingFundPageResponse list(UUID workspaceId, SinkingFundStatus status, int page, int size, UUID userId) {
        requireActiveMember(workspaceId, userId);
        Page<SinkingFund> result = status == null
                ? fundRepository.findAllByWorkspaceId(workspaceId, pageRequest(page, size))
                : fundRepository.findAllByWorkspaceIdAndStatus(workspaceId, status, pageRequest(page, size));
        return SinkingFundPageResponse.builder()
                .content(result.getContent().stream().map(this::mapFund).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .first(result.isFirst())
                .last(result.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public SinkingFundResponse get(UUID workspaceId, UUID fundId, UUID userId) {
        requireActiveMember(workspaceId, userId);
        return mapFund(findInWorkspace(workspaceId, fundId));
    }

    @Transactional
    public SinkingFundResponse create(UUID workspaceId, SinkingFundRequest req, UUID userId) {
        WorkspaceMember member = requireWritableMember(workspaceId, userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));
        ValidatedFund validated = validateFund(req, true);
        ensureUniqueOpenName(workspaceId, validated.name(), null);
        SinkingFund fund = SinkingFund.builder()
                .workspace(member.getWorkspace())
                .name(validated.name())
                .description(validated.description())
                .targetAmount(validated.targetAmount())
                .targetDate(validated.targetDate())
                .status(validated.status())
                .createdByUser(user)
                .build();
        try {
            return mapFund(fundRepository.saveAndFlush(fund));
        } catch (DataIntegrityViolationException ex) {
            throw translateIntegrity(ex);
        }
    }

    @Transactional
    public SinkingFundResponse update(UUID workspaceId, UUID fundId, SinkingFundRequest req, UUID userId) {
        requireWritableMember(workspaceId, userId);
        SinkingFund fund = findInWorkspaceForUpdate(workspaceId, fundId);
        ValidatedFund validated = validateFund(req, false);
        ensureUniqueOpenName(workspaceId, validated.name(), fundId);
        fund.setName(validated.name());
        fund.setDescription(validated.description());
        fund.setTargetAmount(validated.targetAmount());
        fund.setTargetDate(validated.targetDate());
        fund.setStatus(validated.status());
        fund.setUpdatedAt(Instant.now(clock));
        try {
            return mapFund(fundRepository.saveAndFlush(fund));
        } catch (DataIntegrityViolationException ex) {
            throw translateIntegrity(ex);
        }
    }

    @Transactional
    public SinkingFundAllocationResponse allocate(UUID workspaceId, UUID fundId, SinkingFundAllocationRequest req, UUID userId) {
        WorkspaceMember member = requireWritableMember(workspaceId, userId);
        SinkingFund fund = findInWorkspaceForUpdate(workspaceId, fundId);
        if (fund.getStatus() == SinkingFundStatus.COMPLETED || fund.getStatus() == SinkingFundStatus.ARCHIVED) {
            throw new BusinessException("SINKING_FUND_READ_ONLY", "Completed or archived sinking fund cannot accept allocations", HttpStatus.CONFLICT);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));
        ValidatedAllocation validated = validateAllocation(req);
        BigDecimal current = reserved(workspaceId, fundId);
        BigDecimal next = current.add(validated.amountDelta());
        if (next.signum() < 0) {
            throw new BusinessException("SINKING_FUND_RESERVED_AMOUNT_NEGATIVE", "Reserved amount cannot be below zero", HttpStatus.CONFLICT);
        }
        SinkingFundAllocation allocation = SinkingFundAllocation.builder()
                .workspace(member.getWorkspace())
                .sinkingFund(fund)
                .allocationType(validated.type())
                .amountDelta(validated.amountDelta())
                .note(validated.note())
                .actorUser(user)
                .occurredAt(Instant.now(clock))
                .build();
        return mapAllocation(allocationRepository.saveAndFlush(allocation), next);
    }

    @Transactional(readOnly = true)
    public SinkingFundAllocationPageResponse history(UUID workspaceId, UUID fundId, int page, int size, UUID userId) {
        requireActiveMember(workspaceId, userId);
        findInWorkspace(workspaceId, fundId);
        Page<SinkingFundAllocation> result = allocationRepository.findAllByWorkspaceIdAndSinkingFundIdOrderByOccurredAtDescCreatedAtDescIdDesc(
                workspaceId, fundId, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        return SinkingFundAllocationPageResponse.builder()
                .content(result.getContent().stream().map(allocation -> mapAllocation(allocation, null)).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .first(result.isFirst())
                .last(result.isLast())
                .build();
    }

    private ValidatedFund validateFund(SinkingFundRequest req, boolean create) {
        if (req == null) {
            throw new BusinessException("VALIDATION_ERROR", "Request body is required");
        }
        String name = normalize(req.getName());
        if (name == null || name.isBlank() || name.length() > MAX_NAME_LENGTH) {
            throw new BusinessException("VALIDATION_ERROR", "Sinking fund name is invalid", Map.of("name", "Sinking fund name is invalid"));
        }
        String description = normalize(req.getDescription());
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new BusinessException("VALIDATION_ERROR", "Sinking fund description is too long", Map.of("description", "Sinking fund description is too long"));
        }
        BigDecimal targetAmount = req.getTargetAmount();
        if (targetAmount != null && targetAmount.signum() <= 0) {
            throw new BusinessException("VALIDATION_ERROR", "Sinking fund target amount is invalid", Map.of("targetAmount", "Target amount must be positive"));
        }
        SinkingFundStatus status = req.getStatus() == null || req.getStatus().isBlank()
                ? (create ? SinkingFundStatus.ACTIVE : null)
                : parseStatus(req.getStatus());
        if (status == null) {
            throw new BusinessException("VALIDATION_ERROR", "Sinking fund status is required");
        }
        return new ValidatedFund(name, description, targetAmount, req.getTargetDate(), status);
    }

    private ValidatedAllocation validateAllocation(SinkingFundAllocationRequest req) {
        if (req == null) {
            throw new BusinessException("VALIDATION_ERROR", "Request body is required");
        }
        SinkingFundAllocationType type = parseAllocationType(req.getType());
        BigDecimal amount = req.getAmount();
        if (amount == null || amount.signum() == 0) {
            throw new BusinessException("VALIDATION_ERROR", "Allocation amount is invalid", Map.of("amount", "Allocation amount is required"));
        }
        String note = normalize(req.getNote());
        if (note != null && note.length() > MAX_NOTE_LENGTH) {
            throw new BusinessException("VALIDATION_ERROR", "Allocation note is too long", Map.of("note", "Allocation note is too long"));
        }
        if (type == SinkingFundAllocationType.ADJUST && note == null) {
            throw new BusinessException("VALIDATION_ERROR", "Adjustment note is required", Map.of("note", "Adjustment note is required"));
        }
        BigDecimal delta = switch (type) {
            case ALLOCATE -> requirePositive(amount, "ALLOCATE");
            case RELEASE -> requirePositive(amount, "RELEASE").negate();
            case ADJUST -> amount;
        };
        return new ValidatedAllocation(type, delta, note);
    }

    private BigDecimal requirePositive(BigDecimal amount, String type) {
        if (amount.signum() <= 0) {
            throw new BusinessException("VALIDATION_ERROR", type + " amount must be positive", Map.of("amount", type + " amount must be positive"));
        }
        return amount;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private SinkingFundStatus parseStatus(String status) {
        try {
            return SinkingFundStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("VALIDATION_ERROR", "Sinking fund status is invalid");
        }
    }

    private SinkingFundAllocationType parseAllocationType(String type) {
        if (type == null || type.isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "Allocation type is required");
        }
        try {
            return SinkingFundAllocationType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("VALIDATION_ERROR", "Allocation type is invalid");
        }
    }

    private void ensureUniqueOpenName(UUID workspaceId, String name, UUID excludeId) {
        boolean exists = excludeId == null
                ? fundRepository.existsOpenNameInWorkspace(workspaceId, name)
                : fundRepository.existsOpenNameInWorkspaceExcluding(workspaceId, name, excludeId);
        if (exists) {
            throw new BusinessException("SINKING_FUND_NAME_ALREADY_EXISTS", "Sinking fund name already exists", HttpStatus.CONFLICT);
        }
    }

    private SinkingFund findInWorkspace(UUID workspaceId, UUID fundId) {
        return fundRepository.findByIdAndWorkspaceId(fundId, workspaceId)
                .orElseThrow(() -> new BusinessException("SINKING_FUND_NOT_FOUND", "Sinking fund not found", HttpStatus.NOT_FOUND));
    }

    private SinkingFund findInWorkspaceForUpdate(UUID workspaceId, UUID fundId) {
        return fundRepository.findByIdAndWorkspaceIdForUpdate(fundId, workspaceId)
                .orElseThrow(() -> new BusinessException("SINKING_FUND_NOT_FOUND", "Sinking fund not found", HttpStatus.NOT_FOUND));
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
            throw new BusinessException("FORBIDDEN", "Viewer cannot modify sinking funds", HttpStatus.FORBIDDEN);
        }
        return member;
    }

    private RuntimeException translateIntegrity(DataIntegrityViolationException ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraint
                    && constraint.getConstraintName() != null
                    && constraint.getConstraintName().contains(OPEN_NAME_INDEX)) {
                return new BusinessException("SINKING_FUND_NAME_ALREADY_EXISTS", "Sinking fund name already exists", HttpStatus.CONFLICT);
            }
            String message = current.getMessage();
            if (message != null && message.contains(OPEN_NAME_INDEX)) {
                return new BusinessException("SINKING_FUND_NAME_ALREADY_EXISTS", "Sinking fund name already exists", HttpStatus.CONFLICT);
            }
            current = current.getCause();
        }
        return ex;
    }

    private PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));
    }

    private SinkingFundResponse mapFund(SinkingFund fund) {
        Workspace workspace = fund.getWorkspace();
        return SinkingFundResponse.builder()
                .id(fund.getId())
                .workspaceId(workspace.getId())
                .name(fund.getName())
                .description(fund.getDescription())
                .targetAmount(fund.getTargetAmount())
                .targetDate(fund.getTargetDate())
                .status(fund.getStatus())
                .createdByUserId(fund.getCreatedByUser().getId())
                .createdAt(fund.getCreatedAt())
                .updatedAt(fund.getUpdatedAt())
                .version(fund.getVersion())
                .build();
    }

    private SinkingFundAllocationResponse mapAllocation(SinkingFundAllocation allocation, BigDecimal reservedAmount) {
        return SinkingFundAllocationResponse.builder()
                .id(allocation.getId())
                .workspaceId(allocation.getWorkspace().getId())
                .sinkingFundId(allocation.getSinkingFund().getId())
                .type(allocation.getAllocationType())
                .amountDelta(allocation.getAmountDelta())
                .reservedAmount(reservedAmount)
                .note(allocation.getNote())
                .actorUserId(allocation.getActorUser().getId())
                .occurredAt(allocation.getOccurredAt())
                .createdAt(allocation.getCreatedAt())
                .build();
    }

    private BigDecimal reserved(UUID workspaceId, UUID fundId) {
        BigDecimal amount = allocationRepository.sumReservedAmount(workspaceId, fundId);
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private record ValidatedFund(String name, String description, BigDecimal targetAmount,
                                 LocalDate targetDate, SinkingFundStatus status) {
    }

    private record ValidatedAllocation(SinkingFundAllocationType type, BigDecimal amountDelta, String note) {
    }
}
