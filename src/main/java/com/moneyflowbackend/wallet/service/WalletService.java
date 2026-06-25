package com.moneyflowbackend.wallet.service;

import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.transaction.model.TransactionStatus;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.wallet.dto.WalletRequest;
import com.moneyflowbackend.wallet.dto.WalletResponse;
import com.moneyflowbackend.wallet.dto.WalletSummaryResponse;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.model.WalletType;
import com.moneyflowbackend.wallet.repository.WalletRepository;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public WalletService(
            WalletRepository walletRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository) {
        this.walletRepository = walletRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
    }

    @Transactional(readOnly = true)
    public List<WalletResponse> list(UUID workspaceId, UUID userId) {
        requireActiveMember(workspaceId, userId);
        return sortedWallets(workspaceId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WalletResponse get(UUID workspaceId, UUID walletId, UUID userId) {
        requireActiveMember(workspaceId, userId);
        return mapToResponse(findWalletInWorkspace(workspaceId, walletId));
    }

    @Transactional(readOnly = true)
    public WalletSummaryResponse summary(UUID workspaceId, UUID userId) {
        requireActiveMember(workspaceId, userId);
        List<WalletResponse> wallets = sortedWallets(workspaceId).stream()
                .map(this::mapToResponse)
                .toList();
        BigDecimal totalBalance = wallets.stream()
                .filter(WalletResponse::isActive)
                .filter(WalletResponse::isIncludeInTotal)
                .map(WalletResponse::getCurrentBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long activeWalletCount = wallets.stream()
                .filter(WalletResponse::isActive)
                .count();
        UUID defaultWalletId = wallets.stream()
                .filter(WalletResponse::isActive)
                .filter(WalletResponse::isDefault)
                .map(WalletResponse::getId)
                .findFirst()
                .orElse(null);

        return WalletSummaryResponse.builder()
                .totalBalance(totalBalance)
                .activeWalletCount(activeWalletCount)
                .defaultWalletId(defaultWalletId)
                .build();
    }

    @Transactional
    public WalletResponse create(UUID workspaceId, WalletRequest req, UUID userId) {
        WorkspaceMember member = requireWritableMember(workspaceId, userId);
        String name = normalizeName(req.getName());
        WalletType walletType = parseWalletType(req.getWalletType());
        BigDecimal openingBalance = normalizeAmount(req.getOpeningBalance());
        validateOpeningDate(openingBalance, req.getOpeningDate());
        ensureUniqueName(workspaceId, name, null);

        boolean makeDefault = walletRepository.countByWorkspaceId(workspaceId) == 0 || Boolean.TRUE.equals(req.getIsDefault());
        Wallet wallet = Wallet.builder()
                .workspace(member.getWorkspace())
                .name(name)
                .walletType(walletType)
                .openingBalance(openingBalance)
                .openingDate(req.getOpeningDate())
                .isDefault(false)
                .isActive(true)
                .includeInTotal(req.getIncludeInTotal() == null || req.getIncludeInTotal())
                .build();

        wallet = walletRepository.save(wallet);
        if (makeDefault) {
            wallet = setDefaultInternal(workspaceId, wallet);
        }
        return mapToResponse(wallet);
    }

    @Transactional
    public WalletResponse update(UUID workspaceId, UUID walletId, WalletRequest req, UUID userId) {
        requireWritableMember(workspaceId, userId);
        Wallet wallet = findWalletInWorkspace(workspaceId, walletId);
        String name = normalizeName(req.getName());
        WalletType walletType = parseWalletType(req.getWalletType());
        BigDecimal openingBalance = normalizeAmount(req.getOpeningBalance());
        validateOpeningDate(openingBalance, req.getOpeningDate());
        ensureUniqueName(workspaceId, name, walletId);

        wallet.setName(name);
        wallet.setWalletType(walletType);
        wallet.setOpeningBalance(openingBalance);
        wallet.setOpeningDate(req.getOpeningDate());
        wallet.setIncludeInTotal(req.getIncludeInTotal() == null || req.getIncludeInTotal());
        wallet.setUpdatedAt(Instant.now());

        wallet = walletRepository.save(wallet);
        return mapToResponse(wallet);
    }

    @Transactional
    public void setDefault(UUID workspaceId, UUID walletId, UUID userId) {
        requireWritableMember(workspaceId, userId);
        Wallet wallet = findWalletInWorkspace(workspaceId, walletId);
        if (!wallet.isActive()) {
            throw new BusinessException("WALLET_INACTIVE", "Ví đã ngưng hoạt động");
        }
        setDefaultInternal(workspaceId, wallet);
    }

    @Transactional
    public void toggleStatus(UUID workspaceId, UUID walletId, boolean active, UUID userId) {
        requireWritableMember(workspaceId, userId);
        Wallet wallet = findWalletInWorkspace(workspaceId, walletId);

        if (!active) {
            if (wallet.isDefault()) {
                throw new BusinessException("DEFAULT_WALLET_CANNOT_BE_DISABLED", "Không thể tắt ví mặc định");
            }
            if (wallet.isActive() && walletRepository.countByWorkspaceIdAndIsActiveTrue(workspaceId) <= 1) {
                throw new BusinessException("LAST_ACTIVE_WALLET_CANNOT_BE_DISABLED", "Workspace phải còn ít nhất một ví đang hoạt động");
            }
        }

        wallet.setActive(active);
        wallet.setUpdatedAt(Instant.now());
        walletRepository.save(wallet);
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateCurrentBalance(UUID walletId) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new BusinessException("WALLET_NOT_FOUND", "Không tìm thấy ví"));
        return calculateCurrentBalance(wallet);
    }

    private BigDecimal calculateCurrentBalance(Wallet wallet) {
        UUID walletId = wallet.getId();
        LocalDate openingDate = wallet.getOpeningDate();

        BigDecimal income = sumWalletTransactions(walletId, openingDate, List.of(
                TransactionType.INCOME,
                TransactionType.LOAN_COLLECTION,
                TransactionType.BORROWING_RECEIPT));
        BigDecimal expense = sumWalletTransactions(walletId, openingDate, List.of(
                TransactionType.EXPENSE,
                TransactionType.LOAN_DISBURSEMENT,
                TransactionType.BORROWING_REPAYMENT));
        BigDecimal transferTo = sumTransfer(walletId, openingDate, true);
        BigDecimal transferFrom = sumTransfer(walletId, openingDate, false);

        return wallet.getOpeningBalance()
                .add(income)
                .subtract(expense)
                .add(transferTo)
                .subtract(transferFrom);
    }

    private WorkspaceMember requireActiveMember(UUID workspaceId, UUID userId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .filter(ws -> ws.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException("WORKSPACE_NOT_FOUND", "Workspace không tồn tại"));
        return workspaceMemberRepository.findByWorkspaceIdAndUserIdAndMemberStatus(workspaceId, userId, "ACTIVE")
                .orElseThrow(() -> new BusinessException("WORKSPACE_ACCESS_DENIED", "Bạn không có quyền truy cập workspace này", HttpStatus.FORBIDDEN));
    }

    private WorkspaceMember requireWritableMember(UUID workspaceId, UUID userId) {
        WorkspaceMember member = requireActiveMember(workspaceId, userId);
        if (member.getRole() == WorkspaceRole.VIEWER) {
            throw new BusinessException("FORBIDDEN", "Tài khoản Viewer không có quyền thay đổi ví", HttpStatus.FORBIDDEN);
        }
        return member;
    }

    private Wallet findWalletInWorkspace(UUID workspaceId, UUID walletId) {
        return walletRepository.findByIdAndWorkspaceId(walletId, workspaceId)
                .orElseThrow(() -> new BusinessException("WALLET_NOT_FOUND", "Không tìm thấy ví"));
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim();
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private WalletType parseWalletType(String type) {
        if (type == null || type.isBlank()) {
            throw new BusinessException(
                    "INVALID_WALLET_TYPE",
                    "Invalid wallet type",
                    Map.of("type", "Invalid wallet type"));
        }
        try {
            return WalletType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(
                    "INVALID_WALLET_TYPE",
                    "Invalid wallet type",
                    Map.of("type", "Invalid wallet type"));
        }
    }

    private List<Wallet> sortedWallets(UUID workspaceId) {
        return walletRepository.findAllByWorkspaceIdOrderByCreatedAtAsc(workspaceId).stream()
                .sorted(Comparator
                        .comparing(Wallet::isDefault, Comparator.reverseOrder())
                        .thenComparing(Wallet::isActive, Comparator.reverseOrder())
                        .thenComparing(Wallet::getCreatedAt)
                        .thenComparing(w -> w.getName().toLowerCase(Locale.ROOT)))
                .toList();
    }

    private void validateOpeningDate(BigDecimal openingBalance, LocalDate openingDate) {
        if (openingBalance.compareTo(BigDecimal.ZERO) != 0 && openingDate == null) {
            throw new BusinessException(
                    "VALIDATION_ERROR",
                    "Vui lòng nhập ngày bắt đầu khi số dư ban đầu khác 0",
                    Map.of("openingDate", "Ngày bắt đầu là bắt buộc khi số dư ban đầu khác 0"));
        }
    }

    private void ensureUniqueName(UUID workspaceId, String name, UUID excludeId) {
        boolean exists = excludeId == null
                ? walletRepository.existsName(workspaceId, name)
                : walletRepository.existsNameExcluding(workspaceId, name, excludeId);
        if (exists) {
            throw new BusinessException(
                    "WALLET_NAME_ALREADY_EXISTS",
                    "Tên ví đã tồn tại",
                    Map.of("name", "Tên ví đã tồn tại"));
        }
    }

    private Wallet setDefaultInternal(UUID workspaceId, Wallet wallet) {
        try {
            walletRepository.clearDefault(workspaceId);
            wallet.setDefault(true);
            wallet.setUpdatedAt(Instant.now());
            return walletRepository.saveAndFlush(wallet);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("WALLET_DEFAULT_CONFLICT", "Không thể cập nhật ví mặc định", HttpStatus.CONFLICT);
        }
    }

    private BigDecimal sumWalletTransactions(UUID walletId, LocalDate openingDate, List<TransactionType> types) {
        String dateFilter = openingDate == null ? "" : "AND t.transactionDate >= :openingDate ";
        var query = entityManager.createQuery(
                "SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
                "WHERE t.wallet.id = :walletId " +
                "AND t.transactionStatus = :status " +
                "AND t.deletedAt IS NULL " +
                "AND t.affectsWalletBalance = true " +
                "AND t.transactionType IN :types " +
                dateFilter, BigDecimal.class)
                .setParameter("walletId", walletId)
                .setParameter("status", TransactionStatus.POSTED)
                .setParameter("types", types);
        if (openingDate != null) {
            query.setParameter("openingDate", openingDate);
        }
        return query.getSingleResult();
    }

    private BigDecimal sumTransfer(UUID walletId, LocalDate openingDate, boolean incoming) {
        String walletSide = incoming ? "destinationWallet" : "sourceWallet";
        String dateFilter = openingDate == null ? "" : "AND td.transaction.transactionDate >= :openingDate ";
        var query = entityManager.createQuery(
                "SELECT COALESCE(SUM(td.transaction.amount), 0) FROM TransferDetail td " +
                "WHERE td." + walletSide + ".id = :walletId " +
                "AND td.transaction.transactionType = :type " +
                "AND td.transaction.transactionStatus = :status " +
                "AND td.transaction.deletedAt IS NULL " +
                "AND td.transaction.affectsWalletBalance = true " +
                dateFilter, BigDecimal.class)
                .setParameter("walletId", walletId)
                .setParameter("type", TransactionType.TRANSFER)
                .setParameter("status", TransactionStatus.POSTED);
        if (openingDate != null) {
            query.setParameter("openingDate", openingDate);
        }
        return query.getSingleResult();
    }

    private WalletResponse mapToResponse(Wallet w) {
        return WalletResponse.builder()
                .id(w.getId())
                .name(w.getName())
                .type(w.getWalletType().name())
                .openingBalance(w.getOpeningBalance())
                .openingDate(w.getOpeningDate())
                .currentBalance(calculateCurrentBalance(w))
                .isDefault(w.isDefault())
                .isActive(w.isActive())
                .includeInTotal(w.isIncludeInTotal())
                .build();
    }
}
