package com.moneyflowbackend.jar.service;

import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.jar.dto.JarAllocationRequest;
import com.moneyflowbackend.jar.dto.JarListResponse;
import com.moneyflowbackend.jar.dto.JarMonthlySummaryResponse;
import com.moneyflowbackend.jar.dto.JarReorderRequest;
import com.moneyflowbackend.jar.dto.JarRequest;
import com.moneyflowbackend.jar.dto.JarResponse;
import com.moneyflowbackend.jar.model.Jar;
import com.moneyflowbackend.jar.repository.JarRepository;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class JarService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final JarRepository jarRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    public JarService(
            JarRepository jarRepository,
            CategoryRepository categoryRepository,
            TransactionRepository transactionRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository) {
        this.jarRepository = jarRepository;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
    }

    @Transactional(readOnly = true)
    public JarListResponse list(UUID workspaceId, boolean includeInactive, UUID userId) {
        requireActiveMember(workspaceId, userId);
        List<JarResponse> jars = findJars(workspaceId, includeInactive).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return buildListResponse(jars);
    }

    @Transactional(readOnly = true)
    public JarMonthlySummaryResponse monthlySummary(UUID workspaceId, String rawMonth, UUID userId) {
        requireActiveMember(workspaceId, userId);
        YearMonth month = parseMonth(rawMonth);
        LocalDate startDate = month.atDay(1);
        LocalDate endDate = month.plusMonths(1).atDay(1);
        BigDecimal monthlyIncome = nz(transactionRepository.sumPostedByTypeInMonth(workspaceId, TransactionType.INCOME, startDate, endDate));
        BigDecimal monthlyExpense = nz(transactionRepository.sumPostedByTypeInMonth(workspaceId, TransactionType.EXPENSE, startDate, endDate));

        List<JarMonthlySummaryResponse.Item> items = jarRepository.findAllByWorkspaceIdAndIsActiveTrueOrderByDisplayOrderAscNameAsc(workspaceId).stream()
                .map(jar -> monthlyItem(workspaceId, jar, monthlyIncome, startDate, endDate))
                .toList();

        return JarMonthlySummaryResponse.builder()
                .month(month.toString())
                .monthlyIncome(monthlyIncome)
                .monthlyExpense(monthlyExpense)
                .jarsTotalTargetPercent(items.stream()
                        .map(JarMonthlySummaryResponse.Item::getTargetPercent)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .jarsMappedCategoryCount(categoryRepository.countByWorkspaceIdAndCategoryTypeAndJarIsNotNullAndIsActiveTrue(workspaceId, CategoryType.EXPENSE))
                .unmappedActiveExpenseCategoryCount(categoryRepository.countByWorkspaceIdAndCategoryTypeAndJarIsNullAndIsActiveTrueAndIsArchivedFalse(workspaceId, CategoryType.EXPENSE))
                .inactiveUnmappedCategoryCount(categoryRepository.countInactiveOrArchivedUnmapped(workspaceId, CategoryType.EXPENSE))
                .overallStatus(overallStatus(items))
                .jars(items)
                .build();
    }

    @Transactional(readOnly = true)
    public JarResponse get(UUID workspaceId, UUID jarId, UUID userId) {
        requireActiveMember(workspaceId, userId);
        return mapToResponse(findJarInWorkspace(workspaceId, jarId));
    }

    @Transactional
    public JarResponse create(UUID workspaceId, JarRequest req, UUID userId) {
        requireWritableMember(workspaceId, userId);
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .filter(ws -> ws.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException("WORKSPACE_NOT_FOUND", "Workspace not found", HttpStatus.NOT_FOUND));

        String name = normalizeName(req.getName());
        String code = normalizeCode(req.getCode());
        validateAllocation(req.getAllocationPercent());

        if (jarRepository.existsByWorkspaceIdAndCodeIgnoreCase(workspaceId, code)) {
            throw new BusinessException("JAR_CODE_ALREADY_EXISTS", "Jar code already exists");
        }
        if (jarRepository.existsActiveName(workspaceId, name)) {
            throw new BusinessException("JAR_NAME_ALREADY_EXISTS", "Jar name already exists");
        }

        Jar jar = Jar.builder()
                .workspace(workspace)
                .code(code)
                .name(name)
                .allocationPercent(req.getAllocationPercent())
                .displayOrder(req.getDisplayOrder() != null ? req.getDisplayOrder() : nextDisplayOrder(workspaceId))
                .isActive(true)
                .build();

        return mapToResponse(jarRepository.save(jar));
    }

    @Transactional
    public JarResponse update(UUID workspaceId, UUID jarId, JarRequest req, UUID userId) {
        requireWritableMember(workspaceId, userId);
        Jar jar = findJarInWorkspace(workspaceId, jarId);

        String name = normalizeName(req.getName());
        validateAllocation(req.getAllocationPercent());

        if (jarRepository.existsActiveNameExcluding(workspaceId, name, jarId)) {
            throw new BusinessException("JAR_NAME_ALREADY_EXISTS", "Jar name already exists");
        }

        jar.setName(name);
        jar.setAllocationPercent(req.getAllocationPercent());
        if (req.getDisplayOrder() != null) {
            jar.setDisplayOrder(req.getDisplayOrder());
        }
        jar.setUpdatedAt(Instant.now());

        return mapToResponse(jarRepository.save(jar));
    }

    @Transactional
    public void toggleStatus(UUID workspaceId, UUID jarId, boolean active, UUID userId) {
        requireWritableMember(workspaceId, userId);
        Jar jar = findJarInWorkspace(workspaceId, jarId);

        if (!active && jar.isActive()) {
            if ((jar.getAllocationPercent() == null ? BigDecimal.ZERO : jar.getAllocationPercent()).compareTo(BigDecimal.ZERO) > 0) {
                throw new BusinessException("JAR_DEACTIVATION_BREAKS_ALLOCATION", "Set jar allocation to 0 before deactivating it");
            }
            if (categoryRepository.countByWorkspaceIdAndJarId(workspaceId, jarId) > 0
                    || transactionRepository.countJarUsage(workspaceId, jarId) > 0) {
                throw new BusinessException("JAR_IN_USE", "Jar is used by categories or transactions");
            }
        }

        if (active && !jar.isActive()) {
            BigDecimal activeTotal = jarRepository.findAllByWorkspaceIdAndIsActiveTrue(workspaceId).stream()
                    .filter(existing -> !existing.getId().equals(jarId))
                    .map(Jar::getAllocationPercent)
                    .filter(value -> value != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal requestedTotal = activeTotal.add(jar.getAllocationPercent() == null ? BigDecimal.ZERO : jar.getAllocationPercent());
            if (requestedTotal.compareTo(ONE_HUNDRED) > 0) {
                throw new BusinessException("JAR_ALLOCATION_EXCEEDS_100", "Active jar allocation total cannot exceed 100%");
            }
        }

        jar.setActive(active);
        jar.setUpdatedAt(Instant.now());
        jarRepository.save(jar);
    }

    @Transactional
    public void delete(UUID workspaceId, UUID jarId, UUID userId) {
        requireOwner(workspaceId, userId);
        Jar jar = findJarInWorkspace(workspaceId, jarId);
        if (categoryRepository.countByWorkspaceIdAndJarId(workspaceId, jarId) > 0
                || transactionRepository.countJarUsage(workspaceId, jarId) > 0) {
            throw new BusinessException("JAR_IN_USE", "Jar is used by categories or transactions");
        }
        jarRepository.delete(jar);
    }

    @Transactional
    public JarListResponse reorder(UUID workspaceId, JarReorderRequest req, UUID userId) {
        requireWritableMember(workspaceId, userId);
        Set<UUID> seen = new HashSet<>();
        for (JarReorderRequest.Item item : req.getItems()) {
            if (!seen.add(item.getJarId())) {
                throw new BusinessException("INVALID_REORDER_REQUEST", "Duplicate jar in reorder request");
            }
            Jar jar = findJarInWorkspace(workspaceId, item.getJarId());
            jar.setDisplayOrder(item.getDisplayOrder());
            jar.setUpdatedAt(Instant.now());
            jarRepository.save(jar);
        }
        return list(workspaceId, true, userId);
    }

    @Transactional
    public JarListResponse updateAllocations(UUID workspaceId, JarAllocationRequest req, UUID userId) {
        requireWritableMember(workspaceId, userId);
        Set<UUID> seen = new HashSet<>();
        for (JarAllocationRequest.Item item : req.getItems()) {
            if (!seen.add(item.getJarId())) {
                throw new BusinessException("INVALID_REORDER_REQUEST", "Duplicate jar in allocation request");
            }
            validateAllocation(item.getAllocationPercent());
            Jar jar = findJarInWorkspace(workspaceId, item.getJarId());
            jar.setAllocationPercent(item.getAllocationPercent());
            jar.setUpdatedAt(Instant.now());
            jarRepository.save(jar);
        }
        return list(workspaceId, true, userId);
    }

    private List<Jar> findJars(UUID workspaceId, boolean includeInactive) {
        if (includeInactive) {
            return jarRepository.findAllByWorkspaceIdOrderByDisplayOrderAscNameAsc(workspaceId);
        }
        return jarRepository.findAllByWorkspaceIdAndIsActiveTrueOrderByDisplayOrderAscNameAsc(workspaceId);
    }

    private JarListResponse buildListResponse(List<JarResponse> jars) {
        BigDecimal activeTotal = jars.stream()
                .filter(JarResponse::isActive)
                .map(JarResponse::getAllocationPercent)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean valid = activeTotal.compareTo(ONE_HUNDRED) == 0;
        return JarListResponse.builder()
                .jars(jars)
                .activeAllocationTotal(activeTotal)
                .allocationValid(valid)
                .allocationWarning(valid ? null : "Active allocation total is " + activeTotal + "%")
                .build();
    }

    private Jar findJarInWorkspace(UUID workspaceId, UUID jarId) {
        return jarRepository.findByIdAndWorkspaceId(jarId, workspaceId)
                .orElseThrow(() -> new BusinessException("JAR_NOT_FOUND", "Jar not found", HttpStatus.NOT_FOUND));
    }

    private WorkspaceMember requireActiveMember(UUID workspaceId, UUID userId) {
        workspaceRepository.findById(workspaceId)
                .filter(ws -> ws.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException("WORKSPACE_NOT_FOUND", "Workspace not found", HttpStatus.NOT_FOUND));
        return workspaceMemberRepository.findByWorkspaceIdAndUserIdAndMemberStatus(workspaceId, userId, "ACTIVE")
                .or(() -> workspaceMemberRepository.findByWorkspaceIdAndPersonLinkedUserIdAndMemberStatus(workspaceId, userId, "ACTIVE"))
                .orElseThrow(() -> new BusinessException("WORKSPACE_ACCESS_DENIED", "Workspace access denied", HttpStatus.FORBIDDEN));
    }

    private JarMonthlySummaryResponse.Item monthlyItem(
            UUID workspaceId,
            Jar jar,
            BigDecimal monthlyIncome,
            LocalDate startDate,
            LocalDate endDate) {
        BigDecimal targetPercent = nz(jar.getAllocationPercent());
        BigDecimal targetAmount = monthlyIncome.multiply(targetPercent).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal actualAmount = nz(transactionRepository.sumPostedExpenseByJarInMonth(workspaceId, jar.getId(), startDate, endDate));
        long txCount = transactionRepository.countPostedExpenseByJarInMonth(workspaceId, jar.getId(), startDate, endDate);
        BigDecimal remainingAmount = targetAmount.subtract(actualAmount);
        BigDecimal overAmount = actualAmount.subtract(targetAmount).max(BigDecimal.ZERO);
        BigDecimal actualPercent = monthlyIncome.compareTo(BigDecimal.ZERO) > 0
                ? actualAmount.multiply(ONE_HUNDRED).divide(monthlyIncome, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        String status = monthlyStatus(monthlyIncome, actualAmount, targetAmount, txCount);

        return JarMonthlySummaryResponse.Item.builder()
                .jarId(jar.getId())
                .jarCode(jar.getCode())
                .jarName(jar.getName())
                .targetPercent(targetPercent)
                .targetAmount(targetAmount)
                .actualAmount(actualAmount)
                .actualPercentOfIncome(actualPercent)
                .transactionCount(txCount)
                .categoryCount(categoryRepository.countByWorkspaceIdAndJarIdAndIsActiveTrue(workspaceId, jar.getId()))
                .remainingAmount(remainingAmount)
                .overAmount(overAmount)
                .status(status)
                .message(monthlyMessage(status, jar.getCode()))
                .build();
    }

    private String monthlyStatus(BigDecimal monthlyIncome, BigDecimal actualAmount, BigDecimal targetAmount, long txCount) {
        if (monthlyIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return "NO_INCOME";
        }
        if (txCount == 0) {
            return "NO_DATA";
        }
        if (actualAmount.compareTo(targetAmount) <= 0) {
            return "OK";
        }
        if (actualAmount.compareTo(targetAmount.multiply(new BigDecimal("1.1"))) <= 0) {
            return "WARNING";
        }
        return "OVER";
    }

    private String monthlyMessage(String status, String jarCode) {
        return switch (status) {
            case "NO_INCOME" -> "No monthly income to evaluate " + jarCode;
            case "NO_DATA" -> "No spending recorded for " + jarCode;
            case "OK" -> jarCode + " is within target";
            case "WARNING" -> jarCode + " is slightly over target";
            default -> jarCode + " is over target";
        };
    }

    private String overallStatus(List<JarMonthlySummaryResponse.Item> items) {
        if (items.stream().anyMatch(item -> "OVER".equals(item.getStatus()))) return "OVER";
        if (items.stream().anyMatch(item -> "WARNING".equals(item.getStatus()))) return "WARNING";
        if (items.stream().allMatch(item -> "NO_INCOME".equals(item.getStatus()))) return "NO_INCOME";
        if (items.stream().allMatch(item -> "NO_DATA".equals(item.getStatus()))) return "NO_DATA";
        return "OK";
    }

    private YearMonth parseMonth(String rawMonth) {
        try {
            return YearMonth.parse(rawMonth == null ? "" : rawMonth.trim());
        } catch (DateTimeParseException ex) {
            throw new BusinessException("INVALID_MONTH", "Month must use yyyy-MM format");
        }
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private WorkspaceMember requireWritableMember(UUID workspaceId, UUID userId) {
        return requireOwner(workspaceId, userId);
    }

    private WorkspaceMember requireOwner(UUID workspaceId, UUID userId) {
        WorkspaceMember member = requireActiveMember(workspaceId, userId);
        if (member.getRole() != WorkspaceRole.OWNER) {
            throw new BusinessException("FORBIDDEN", "Only workspace owner can modify jars", HttpStatus.FORBIDDEN);
        }
        return member;
    }

    private String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "Jar name is required");
        }
        return normalized;
    }

    private String normalizeCode(String code) {
        String normalized = code == null ? "" : code.trim().replaceAll("\\s+", "_").toUpperCase();
        if (normalized.isEmpty() || !normalized.matches("[A-Z0-9_]{1,20}")) {
            throw new BusinessException("INVALID_JAR_CODE", "Invalid jar code");
        }
        return normalized;
    }

    private void validateAllocation(BigDecimal allocationPercent) {
        if (allocationPercent != null
                && (allocationPercent.compareTo(BigDecimal.ZERO) < 0 || allocationPercent.compareTo(ONE_HUNDRED) > 0)) {
            throw new BusinessException("INVALID_ALLOCATION_PERCENT", "Allocation percent must be between 0 and 100");
        }
    }

    private int nextDisplayOrder(UUID workspaceId) {
        return jarRepository.findAllByWorkspaceIdOrderByDisplayOrderAscNameAsc(workspaceId).stream()
                .map(Jar::getDisplayOrder)
                .filter(order -> order != null)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private JarResponse mapToResponse(Jar j) {
        return JarResponse.builder()
                .id(j.getId())
                .workspaceId(j.getWorkspace().getId())
                .code(j.getCode())
                .name(j.getName())
                .allocationPercent(j.getAllocationPercent())
                .displayOrder(j.getDisplayOrder())
                .isActive(j.isActive())
                .categoryCount(categoryRepository.countByWorkspaceIdAndJarIdAndIsActiveTrue(j.getWorkspace().getId(), j.getId()))
                .usageCount(transactionRepository.countJarUsage(j.getWorkspace().getId(), j.getId()))
                .createdAt(j.getCreatedAt())
                .updatedAt(j.getUpdatedAt())
                .build();
    }
}
