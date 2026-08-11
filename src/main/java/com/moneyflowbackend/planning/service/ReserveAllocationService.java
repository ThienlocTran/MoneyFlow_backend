package com.moneyflowbackend.planning.service;

import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.jar.model.Jar;
import com.moneyflowbackend.jar.repository.JarRepository;
import com.moneyflowbackend.planning.dto.ReserveAllocationActionRequest;
import com.moneyflowbackend.planning.dto.ReserveAllocationListResponse;
import com.moneyflowbackend.planning.dto.ReserveAllocationRequest;
import com.moneyflowbackend.planning.dto.ReserveAllocationResponse;
import com.moneyflowbackend.planning.dto.ReserveAllocationUpdateRequest;
import com.moneyflowbackend.planning.model.ReserveAllocation;
import com.moneyflowbackend.planning.model.ReserveAllocationStatus;
import com.moneyflowbackend.planning.model.ReservePurposeType;
import com.moneyflowbackend.planning.repository.ReserveAllocationRepository;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.repository.WalletRepository;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.service.WorkspaceService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ReserveAllocationService {
    private static final int LIST_LIMIT = 100;
    private static final List<String> BALANCE_WARNING = List.of("RESERVE_BALANCE_CHECK_UNAVAILABLE");

    private final ReserveAllocationRepository reserveRepository;
    private final WorkspaceService workspaceService;
    private final WalletRepository walletRepository;
    private final CategoryRepository categoryRepository;
    private final JarRepository jarRepository;
    private final Clock clock;

    public ReserveAllocationService(ReserveAllocationRepository reserveRepository,
                                    WorkspaceService workspaceService,
                                    WalletRepository walletRepository,
                                    CategoryRepository categoryRepository,
                                    JarRepository jarRepository,
                                    Clock clock) {
        this.reserveRepository = reserveRepository;
        this.workspaceService = workspaceService;
        this.walletRepository = walletRepository;
        this.categoryRepository = categoryRepository;
        this.jarRepository = jarRepository;
        this.clock = clock;
    }

    @Transactional
    public ReserveAllocationResponse create(UUID workspaceId, ReserveAllocationRequest request, UUID userId) {
        WorkspaceMember member = workspaceService.requireWritableMember(workspaceId, userId);
        ReserveAllocation reserve = ReserveAllocation.builder()
                .workspace(member.getWorkspace())
                .createdByUser(member.getUser())
                .name(requiredName(request == null ? null : request.name()))
                .amount(requiredAmount(request == null ? null : request.amount()))
                .currency(currency(request == null ? null : request.currency()))
                .purposeType(parsePurpose(request == null ? null : request.purposeType()))
                .wallet(wallet(workspaceId, request == null ? null : request.walletId()))
                .category(category(workspaceId, request == null ? null : request.categoryId()))
                .jar(jar(workspaceId, request == null ? null : request.jarId()))
                .targetDate(request == null ? null : request.targetDate())
                .note(note(request == null ? null : request.note()))
                .build();
        return map(reserveRepository.saveAndFlush(reserve));
    }

    @Transactional(readOnly = true)
    public ReserveAllocationListResponse list(UUID workspaceId, String status, String purposeType, boolean includeInactive,
                                              LocalDate fromTargetDate, LocalDate toTargetDate, UUID userId) {
        workspaceService.verifyMembership(workspaceId, userId);
        validateRange(fromTargetDate, toTargetDate);
        var rows = reserveRepository.search(
                workspaceId,
                parseOptionalStatus(status),
                parseOptionalPurpose(purposeType),
                includeInactive,
                fromTargetDate,
                toTargetDate,
                PageRequest.of(0, LIST_LIMIT));
        BigDecimal active = total(rows, ReserveAllocationStatus.ACTIVE);
        BigDecimal released = total(rows, ReserveAllocationStatus.RELEASED);
        BigDecimal cancelled = total(rows, ReserveAllocationStatus.CANCELLED);
        String responseCurrency = rows.isEmpty() ? "VND" : rows.get(0).getCurrency();
        return new ReserveAllocationListResponse(rows.stream().map(this::map).toList(), rows.size(), LIST_LIMIT,
                active, released, cancelled, responseCurrency, BALANCE_WARNING);
    }

    @Transactional(readOnly = true)
    public ReserveAllocationResponse get(UUID workspaceId, UUID reserveId, UUID userId) {
        workspaceService.verifyMembership(workspaceId, userId);
        return map(reserve(workspaceId, reserveId));
    }

    @Transactional
    public ReserveAllocationResponse update(UUID workspaceId, UUID reserveId, ReserveAllocationUpdateRequest request, UUID userId) {
        workspaceService.requireWritableMember(workspaceId, userId);
        ReserveAllocation reserve = reserve(workspaceId, reserveId);
        if (reserve.getStatus() != ReserveAllocationStatus.ACTIVE) {
            throw new BusinessException("RESERVE_CANNOT_UPDATE_INACTIVE", "Inactive reserve cannot be updated");
        }
        if (request == null) {
            return map(reserve);
        }
        if (request.name() != null) reserve.setName(requiredName(request.name()));
        if (request.amount() != null) reserve.setAmount(requiredAmount(request.amount()));
        if (request.currency() != null) reserve.setCurrency(currency(request.currency()));
        if (request.purposeType() != null) reserve.setPurposeType(parsePurpose(request.purposeType()));
        if (request.walletId() != null) reserve.setWallet(wallet(workspaceId, request.walletId()));
        if (request.categoryId() != null) reserve.setCategory(category(workspaceId, request.categoryId()));
        if (request.jarId() != null) reserve.setJar(jar(workspaceId, request.jarId()));
        if (request.targetDate() != null) reserve.setTargetDate(request.targetDate());
        if (request.note() != null) reserve.setNote(note(request.note()));
        return map(reserveRepository.saveAndFlush(reserve));
    }

    @Transactional
    public ReserveAllocationResponse release(UUID workspaceId, UUID reserveId, ReserveAllocationActionRequest request, UUID userId) {
        workspaceService.requireWritableMember(workspaceId, userId);
        ReserveAllocation reserve = reserve(workspaceId, reserveId);
        if (reserve.getStatus() == ReserveAllocationStatus.RELEASED) {
            return map(reserve);
        }
        if (reserve.getStatus() == ReserveAllocationStatus.CANCELLED) {
            throw new BusinessException("RESERVE_CANCELLED_CANNOT_RELEASE", "Cancelled reserve cannot be released");
        }
        reserve.setStatus(ReserveAllocationStatus.RELEASED);
        reserve.setReleasedAt(Instant.now(clock));
        String actionNote = trim(request == null ? null : request.note(), 1000);
        if (actionNote != null) reserve.setNote(actionNote);
        return map(reserveRepository.saveAndFlush(reserve));
    }

    @Transactional
    public ReserveAllocationResponse cancel(UUID workspaceId, UUID reserveId, ReserveAllocationActionRequest request, UUID userId) {
        workspaceService.requireWritableMember(workspaceId, userId);
        ReserveAllocation reserve = reserve(workspaceId, reserveId);
        if (reserve.getStatus() == ReserveAllocationStatus.CANCELLED) {
            return map(reserve);
        }
        if (reserve.getStatus() == ReserveAllocationStatus.RELEASED) {
            throw new BusinessException("RESERVE_RELEASED_CANNOT_CANCEL", "Released reserve cannot be cancelled");
        }
        reserve.setStatus(ReserveAllocationStatus.CANCELLED);
        reserve.setCancelledAt(Instant.now(clock));
        String reason = trim(request == null ? null : request.reason(), 1000);
        if (reason != null) reserve.setNote(reason);
        return map(reserveRepository.saveAndFlush(reserve));
    }

    private ReserveAllocation reserve(UUID workspaceId, UUID reserveId) {
        return reserveRepository.findByIdAndWorkspaceIdAndDeletedAtIsNull(reserveId, workspaceId)
                .orElseThrow(() -> new BusinessException("RESERVE_NOT_FOUND", "Reserve allocation not found", HttpStatus.NOT_FOUND));
    }

    private String requiredName(String raw) {
        String value = trim(raw, 120);
        if (value == null) {
            throw new BusinessException("RESERVE_NAME_REQUIRED", "Reserve name is required");
        }
        return value;
    }

    private BigDecimal requiredAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("RESERVE_AMOUNT_INVALID", "Reserve amount must be greater than zero");
        }
        return amount;
    }

    private String currency(String raw) {
        String value = raw == null || raw.isBlank() ? "VND" : raw.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z]{3}")) {
            throw new BusinessException("RESERVE_CURRENCY_INVALID", "Currency must use a 3-letter code");
        }
        return value;
    }

    private String note(String raw) {
        return trim(raw, 1000);
    }

    private String trim(String raw, int max) {
        if (raw == null) return null;
        String value = raw.trim().replaceAll("\\s+", " ");
        if (value.isEmpty()) return null;
        return value.length() > max ? value.substring(0, max) : value;
    }

    private ReservePurposeType parsePurpose(String raw) {
        return raw == null || raw.isBlank() ? ReservePurposeType.CUSTOM : parseOptionalPurpose(raw);
    }

    private ReservePurposeType parseOptionalPurpose(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return ReservePurposeType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("RESERVE_PURPOSE_TYPE_INVALID", "Reserve purpose type is invalid");
        }
    }

    private ReserveAllocationStatus parseOptionalStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return ReserveAllocationStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("RESERVE_STATUS_INVALID", "Reserve status is invalid");
        }
    }

    private Wallet wallet(UUID workspaceId, UUID walletId) {
        if (walletId == null) return null;
        return walletRepository.findByIdAndWorkspaceId(walletId, workspaceId)
                .orElseThrow(() -> new BusinessException("RESERVE_WALLET_NOT_FOUND", "Wallet is missing or inaccessible", HttpStatus.NOT_FOUND));
    }

    private Category category(UUID workspaceId, UUID categoryId) {
        if (categoryId == null) return null;
        return categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)
                .orElseThrow(() -> new BusinessException("RESERVE_CATEGORY_NOT_FOUND", "Category is missing or inaccessible", HttpStatus.NOT_FOUND));
    }

    private Jar jar(UUID workspaceId, UUID jarId) {
        if (jarId == null) return null;
        return jarRepository.findByIdAndWorkspaceId(jarId, workspaceId)
                .orElseThrow(() -> new BusinessException("RESERVE_JAR_NOT_FOUND", "Jar is missing or inaccessible", HttpStatus.NOT_FOUND));
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new BusinessException("RESERVE_TARGET_DATE_RANGE_INVALID", "Reserve target date range is invalid");
        }
    }

    private BigDecimal total(List<ReserveAllocation> rows, ReserveAllocationStatus status) {
        return rows.stream()
                .filter(r -> r.getStatus() == status)
                .map(ReserveAllocation::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private ReserveAllocationResponse map(ReserveAllocation reserve) {
        Wallet wallet = reserve.getWallet();
        Category category = reserve.getCategory();
        Jar jar = reserve.getJar();
        return new ReserveAllocationResponse(
                reserve.getId(),
                reserve.getWorkspace().getId(),
                reserve.getName(),
                reserve.getAmount(),
                reserve.getCurrency(),
                reserve.getStatus(),
                reserve.getPurposeType(),
                wallet == null ? null : wallet.getId(),
                wallet == null ? null : wallet.getName(),
                category == null ? null : category.getId(),
                category == null ? null : category.getName(),
                jar == null ? null : jar.getId(),
                jar == null ? null : jar.getName(),
                reserve.getTargetDate(),
                reserve.getNote(),
                BALANCE_WARNING,
                reserve.getReleasedAt(),
                reserve.getCancelledAt(),
                reserve.getCreatedAt(),
                reserve.getUpdatedAt());
    }
}
