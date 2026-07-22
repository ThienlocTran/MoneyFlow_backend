package com.moneyflowbackend.savingsgoal.service;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.savingsgoal.dto.SavingsGoalPageResponse;
import com.moneyflowbackend.savingsgoal.dto.SavingsGoalLedgerEntryResponse;
import com.moneyflowbackend.savingsgoal.dto.SavingsGoalLedgerPageResponse;
import com.moneyflowbackend.savingsgoal.dto.SavingsGoalLedgerRequest;
import com.moneyflowbackend.savingsgoal.dto.SavingsGoalRequest;
import com.moneyflowbackend.savingsgoal.dto.SavingsGoalResponse;
import com.moneyflowbackend.savingsgoal.dto.SavingsGoalSummaryItemResponse;
import com.moneyflowbackend.savingsgoal.dto.SavingsGoalSummaryListResponse;
import com.moneyflowbackend.savingsgoal.dto.SavingsGoalWorkspaceSummaryResponse;
import com.moneyflowbackend.savingsgoal.model.SavingsGoal;
import com.moneyflowbackend.savingsgoal.model.SavingsGoalLedgerEntry;
import com.moneyflowbackend.savingsgoal.model.SavingsGoalLedgerType;
import com.moneyflowbackend.savingsgoal.model.SavingsGoalStatus;
import com.moneyflowbackend.savingsgoal.repository.SavingsGoalLedgerEntryRepository;
import com.moneyflowbackend.savingsgoal.repository.SavingsGoalRepository;
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
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
public class SavingsGoalService {
    private static final int MAX_NAME_LENGTH = 160;
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final int MAX_NOTE_LENGTH = 500;
    private static final String OPEN_NAME_INDEX = "uq_savings_goals_workspace_open_name";

    private final SavingsGoalRepository goalRepository;
    private final SavingsGoalLedgerEntryRepository ledgerRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public SavingsGoalService(
            SavingsGoalRepository goalRepository,
            SavingsGoalLedgerEntryRepository ledgerRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            UserRepository userRepository,
            Clock clock) {
        this.goalRepository = goalRepository;
        this.ledgerRepository = ledgerRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SavingsGoalPageResponse list(UUID workspaceId, SavingsGoalStatus status, String search, boolean includeArchived, int page, int size, UUID userId) {
        WorkspaceMember member = requireActiveMember(workspaceId, userId);
        int pageNumber = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), 100);
        Page<SavingsGoal> result = goalRepository.findGoalPage(
                workspaceId,
                statuses(status, includeArchived),
                searchPattern(search),
                PageRequest.of(pageNumber, pageSize, Sort.by(
                        Sort.Order.asc("status"),
                        Sort.Order.asc("targetDate").nullsLast(),
                        Sort.Order.asc("name"),
                        Sort.Order.asc("createdAt"),
                        Sort.Order.asc("id"))));
        Map<UUID, BigDecimal> reservedAmounts = reservedAmounts(workspaceId, result.getContent());
        return SavingsGoalPageResponse.builder()
                .content(result.getContent().stream()
                        .map(goal -> mapToResponse(goal, member, reservedAmounts.getOrDefault(goal.getId(), BigDecimal.ZERO)))
                        .toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .first(result.isFirst())
                .last(result.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public SavingsGoalResponse get(UUID workspaceId, UUID goalId, UUID userId) {
        WorkspaceMember member = requireActiveMember(workspaceId, userId);
        SavingsGoal goal = findGoal(workspaceId, goalId);
        return mapToResponse(goal, member, ledgerRepository.sumReservedAmount(workspaceId, goalId));
    }

    @Transactional(readOnly = true)
    public SavingsGoalSummaryListResponse summaries(UUID workspaceId, SavingsGoalStatus status, String search, boolean includeArchived, UUID userId) {
        requireActiveMember(workspaceId, userId);
        List<SavingsGoal> goals = goalRepository.findGoalsForSummary(
                workspaceId,
                statuses(status, includeArchived),
                searchPattern(search));
        Map<UUID, BigDecimal> reservedAmounts = reservedAmounts(workspaceId, goals);
        return SavingsGoalSummaryListResponse.builder()
                .items(goals.stream()
                        .map(goal -> mapSummaryItem(goal, reservedAmounts.getOrDefault(goal.getId(), BigDecimal.ZERO)))
                        .toList())
                .build();
    }

    @Transactional(readOnly = true)
    public SavingsGoalSummaryItemResponse summary(UUID workspaceId, UUID goalId, UUID userId) {
        requireActiveMember(workspaceId, userId);
        SavingsGoal goal = findGoal(workspaceId, goalId);
        return mapSummaryItem(goal, ledgerRepository.sumReservedAmount(workspaceId, goalId));
    }

    @Transactional(readOnly = true)
    public SavingsGoalWorkspaceSummaryResponse workspaceSummary(UUID workspaceId, boolean includeArchived, UUID userId) {
        requireActiveMember(workspaceId, userId);
        List<SavingsGoal> goals = goalRepository.findAllByWorkspaceIdAndStatusIn(workspaceId, statuses(null, includeArchived));
        Map<UUID, BigDecimal> reservedAmounts = reservedAmounts(workspaceId, goals);
        BigDecimal target = goals.stream()
                .map(SavingsGoal::getTargetAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal reserved = goals.stream()
                .map(goal -> reservedAmounts.getOrDefault(goal.getId(), BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return SavingsGoalWorkspaceSummaryResponse.builder()
                .goalCount(goals.size())
                .totalTargetAmount(target)
                .totalReservedAmount(reserved)
                .totalRemainingAmount(target.subtract(reserved))
                .progressPercent(progressPercent(reserved, target))
                .build();
    }

    @Transactional
    public SavingsGoalResponse create(UUID workspaceId, SavingsGoalRequest req, UUID userId) {
        WorkspaceMember member = requireWritableMember(workspaceId, userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));
        ValidatedRequest validated = validate(req);
        ensureUniqueOpenName(workspaceId, validated.name(), null);
        SavingsGoal goal = SavingsGoal.builder()
                .workspace(member.getWorkspace())
                .name(validated.name())
                .description(validated.description())
                .targetAmount(validated.targetAmount())
                .targetDate(validated.targetDate())
                .status(SavingsGoalStatus.ACTIVE)
                .createdByUser(user)
                .build();
        try {
            return mapToResponse(goalRepository.saveAndFlush(goal), member, BigDecimal.ZERO);
        } catch (DataIntegrityViolationException ex) {
            throw translateIntegrity(ex);
        }
    }

    @Transactional
    public SavingsGoalResponse update(UUID workspaceId, UUID goalId, SavingsGoalRequest req, UUID userId) {
        WorkspaceMember member = requireWritableMember(workspaceId, userId);
        SavingsGoal goal = findGoalForUpdate(workspaceId, goalId);
        if (goal.getStatus() == SavingsGoalStatus.ARCHIVED) {
            throw archived();
        }
        ValidatedRequest validated = validate(req);
        ensureUniqueOpenName(workspaceId, validated.name(), goalId);
        goal.setName(validated.name());
        goal.setDescription(validated.description());
        goal.setTargetAmount(validated.targetAmount());
        goal.setTargetDate(validated.targetDate());
        goal.setUpdatedAt(Instant.now(clock));
        try {
            return mapToResponse(goalRepository.saveAndFlush(goal), member, ledgerRepository.sumReservedAmount(workspaceId, goalId));
        } catch (DataIntegrityViolationException ex) {
            throw translateIntegrity(ex);
        }
    }

    @Transactional
    public SavingsGoalResponse updateStatus(UUID workspaceId, UUID goalId, SavingsGoalStatus status, UUID userId) {
        WorkspaceMember member = requireWritableMember(workspaceId, userId);
        if (status == null) {
            throw new BusinessException("INVALID_SAVINGS_GOAL_STATUS", "Savings goal status is required");
        }
        SavingsGoal goal = findGoalForUpdate(workspaceId, goalId);
        if (goal.getStatus() != status) {
            goal.setStatus(status);
            goal.setUpdatedAt(Instant.now(clock));
        }
        return mapToResponse(goalRepository.saveAndFlush(goal), member, ledgerRepository.sumReservedAmount(workspaceId, goalId));
    }

    @Transactional
    public SavingsGoalResponse archive(UUID workspaceId, UUID goalId, UUID userId) {
        return updateStatus(workspaceId, goalId, SavingsGoalStatus.ARCHIVED, userId);
    }

    @Transactional(readOnly = true)
    public SavingsGoalLedgerPageResponse ledger(UUID workspaceId, UUID goalId, int page, int size, UUID userId) {
        requireActiveMember(workspaceId, userId);
        findGoal(workspaceId, goalId);
        int pageNumber = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), 100);
        Page<SavingsGoalLedgerEntry> result = ledgerRepository.findAllByWorkspaceIdAndSavingsGoalId(
                workspaceId,
                goalId,
                PageRequest.of(pageNumber, pageSize, Sort.by(
                        Sort.Order.desc("occurredAt"),
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id"))));
        return SavingsGoalLedgerPageResponse.builder()
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
    public SavingsGoalLedgerEntryResponse contribute(UUID workspaceId, UUID goalId, SavingsGoalLedgerRequest req, UUID userId) {
        return addLedgerEntry(workspaceId, goalId, req, userId, SavingsGoalLedgerType.CONTRIBUTION);
    }

    @Transactional
    public SavingsGoalLedgerEntryResponse release(UUID workspaceId, UUID goalId, SavingsGoalLedgerRequest req, UUID userId) {
        return addLedgerEntry(workspaceId, goalId, req, userId, SavingsGoalLedgerType.RELEASE);
    }

    private SavingsGoalLedgerEntryResponse addLedgerEntry(
            UUID workspaceId,
            UUID goalId,
            SavingsGoalLedgerRequest req,
            UUID userId,
            SavingsGoalLedgerType type) {
        WorkspaceMember member = requireWritableMember(workspaceId, userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));
        SavingsGoal goal = findGoalForUpdate(workspaceId, goalId);
        if (goal.getStatus() == SavingsGoalStatus.ARCHIVED) {
            throw archived();
        }
        BigDecimal amount = validateLedgerAmount(req == null ? null : req.getAmount());
        BigDecimal delta = type == SavingsGoalLedgerType.RELEASE ? amount.negate() : amount;
        BigDecimal reserved = ledgerRepository.sumReservedAmount(workspaceId, goalId);
        if (reserved.add(delta).compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("SAVINGS_GOAL_RESERVED_NEGATIVE", "Savings goal reserved amount cannot be negative", HttpStatus.CONFLICT);
        }
        goal.setUpdatedAt(Instant.now(clock));
        SavingsGoalLedgerEntry entry = SavingsGoalLedgerEntry.builder()
                .workspace(member.getWorkspace())
                .savingsGoal(goal)
                .entryType(type)
                .amountDelta(delta)
                .note(validateNote(req == null ? null : req.getNote()))
                .actorUser(user)
                .occurredAt(req == null || req.getOccurredAt() == null ? Instant.now(clock) : req.getOccurredAt())
                .build();
        return mapLedgerEntry(ledgerRepository.saveAndFlush(entry));
    }

    private ValidatedRequest validate(SavingsGoalRequest req) {
        if (req == null) {
            throw new BusinessException("VALIDATION_ERROR", "Request body is required");
        }
        String name = normalizeName(req.getName());
        String description = normalizeText(req.getDescription());
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new BusinessException("VALIDATION_ERROR", "Savings goal description is too long");
        }
        BigDecimal targetAmount = req.getTargetAmount();
        if (targetAmount == null || targetAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_SAVINGS_GOAL_TARGET", "Savings goal target amount must be positive");
        }
        return new ValidatedRequest(name, description, targetAmount, req.getTargetDate());
    }

    private String normalizeName(String name) {
        String value = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (value.isBlank() || value.length() > MAX_NAME_LENGTH) {
            throw new BusinessException("INVALID_SAVINGS_GOAL_NAME", "Savings goal name is invalid");
        }
        return value;
    }

    private String normalizeText(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        return value.isEmpty() ? null : value;
    }

    private String validateNote(String note) {
        String value = normalizeText(note);
        if (value != null && value.length() > MAX_NOTE_LENGTH) {
            throw new BusinessException("VALIDATION_ERROR", "Savings goal ledger note is too long");
        }
        return value;
    }

    private BigDecimal validateLedgerAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_SAVINGS_GOAL_LEDGER_AMOUNT", "Savings goal ledger amount must be positive");
        }
        return amount;
    }

    private String searchPattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private List<SavingsGoalStatus> statuses(SavingsGoalStatus status, boolean includeArchived) {
        if (status != null) {
            return List.of(status);
        }
        return includeArchived
                ? List.of(SavingsGoalStatus.ACTIVE, SavingsGoalStatus.PAUSED, SavingsGoalStatus.COMPLETED, SavingsGoalStatus.ARCHIVED)
                : List.of(SavingsGoalStatus.ACTIVE, SavingsGoalStatus.PAUSED, SavingsGoalStatus.COMPLETED);
    }

    private Map<UUID, BigDecimal> reservedAmounts(UUID workspaceId, List<SavingsGoal> goals) {
        if (goals.isEmpty()) {
            return Map.of();
        }
        return ledgerRepository.sumReservedAmountByGoalIds(workspaceId, goals.stream().map(SavingsGoal::getId).toList())
                .stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (BigDecimal) row[1]));
    }

    private void ensureUniqueOpenName(UUID workspaceId, String name, UUID excludeId) {
        boolean exists = excludeId == null
                ? goalRepository.existsOpenNameInWorkspace(workspaceId, name)
                : goalRepository.existsOpenNameInWorkspaceExcluding(workspaceId, name, excludeId);
        if (exists) {
            throw duplicateName();
        }
    }

    private SavingsGoal findGoal(UUID workspaceId, UUID goalId) {
        return goalRepository.findByIdAndWorkspaceId(goalId, workspaceId)
                .orElseThrow(() -> notFound());
    }

    private SavingsGoal findGoalForUpdate(UUID workspaceId, UUID goalId) {
        return goalRepository.findByIdAndWorkspaceIdForUpdate(goalId, workspaceId)
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
            throw new BusinessException("FORBIDDEN", "Viewer cannot modify savings goals", HttpStatus.FORBIDDEN);
        }
        return member;
    }

    private RuntimeException translateIntegrity(DataIntegrityViolationException ex) {
        if (isOpenNameConstraint(ex)) {
            return duplicateName();
        }
        return ex;
    }

    private boolean isOpenNameConstraint(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraint
                    && constraint.getConstraintName() != null
                    && constraint.getConstraintName().contains(OPEN_NAME_INDEX)) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.contains(OPEN_NAME_INDEX)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private BusinessException notFound() {
        return new BusinessException("SAVINGS_GOAL_NOT_FOUND", "Savings goal not found", HttpStatus.NOT_FOUND);
    }

    private BusinessException duplicateName() {
        return new BusinessException("SAVINGS_GOAL_NAME_ALREADY_EXISTS", "Savings goal name already exists", HttpStatus.CONFLICT);
    }

    private BusinessException archived() {
        return new BusinessException("SAVINGS_GOAL_ARCHIVED", "Archived savings goal cannot be updated", HttpStatus.CONFLICT);
    }

    private SavingsGoalResponse mapToResponse(SavingsGoal goal, WorkspaceMember member, BigDecimal reservedAmount) {
        return SavingsGoalResponse.builder()
                .id(goal.getId())
                .workspaceId(member.getWorkspace().getId())
                .name(goal.getName())
                .description(goal.getDescription())
                .targetAmount(goal.getTargetAmount())
                .reservedAmount(reservedAmount)
                .remainingAmount(goal.getTargetAmount().subtract(reservedAmount))
                .progressPercent(progressPercent(reservedAmount, goal.getTargetAmount()))
                .targetDate(goal.getTargetDate())
                .status(goal.getStatus())
                .createdByUserId(goal.getCreatedByUser().getId())
                .createdAt(goal.getCreatedAt())
                .updatedAt(goal.getUpdatedAt())
                .version(goal.getVersion())
                .build();
    }

    private SavingsGoalSummaryItemResponse mapSummaryItem(SavingsGoal goal, BigDecimal reservedAmount) {
        return SavingsGoalSummaryItemResponse.builder()
                .savingsGoalId(goal.getId())
                .name(goal.getName())
                .status(goal.getStatus())
                .targetAmount(goal.getTargetAmount())
                .reservedAmount(reservedAmount)
                .remainingAmount(goal.getTargetAmount().subtract(reservedAmount))
                .progressPercent(progressPercent(reservedAmount, goal.getTargetAmount()))
                .targetDate(goal.getTargetDate())
                .build();
    }

    private BigDecimal progressPercent(BigDecimal reservedAmount, BigDecimal targetAmount) {
        if (targetAmount == null || targetAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return reservedAmount
                .multiply(new BigDecimal("100"))
                .divide(targetAmount, 2, RoundingMode.HALF_UP);
    }

    private SavingsGoalLedgerEntryResponse mapLedgerEntry(SavingsGoalLedgerEntry entry) {
        return SavingsGoalLedgerEntryResponse.builder()
                .id(entry.getId())
                .workspaceId(entry.getWorkspace().getId())
                .savingsGoalId(entry.getSavingsGoal().getId())
                .entryType(entry.getEntryType())
                .amountDelta(entry.getAmountDelta())
                .note(entry.getNote())
                .actorUserId(entry.getActorUser().getId())
                .occurredAt(entry.getOccurredAt())
                .createdAt(entry.getCreatedAt())
                .version(entry.getVersion())
                .build();
    }

    private record ValidatedRequest(String name, String description, BigDecimal targetAmount, LocalDate targetDate) {
    }
}
