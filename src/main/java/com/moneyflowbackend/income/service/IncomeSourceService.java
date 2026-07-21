package com.moneyflowbackend.income.service;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.income.dto.IncomeSourceRequest;
import com.moneyflowbackend.income.dto.IncomeSourceResponse;
import com.moneyflowbackend.income.dto.IncomeSourceSummaryItemResponse;
import com.moneyflowbackend.income.dto.IncomeSourceSummaryListResponse;
import com.moneyflowbackend.income.dto.IncomeSourceSummaryResponse;
import com.moneyflowbackend.income.model.IncomeSource;
import com.moneyflowbackend.income.model.IncomeSourceStatus;
import com.moneyflowbackend.income.model.IncomeSourceType;
import com.moneyflowbackend.income.repository.IncomeSourceRepository;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class IncomeSourceService {
    private static final int MAX_NAME_LENGTH = 160;
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final String ACTIVE_NAME_INDEX = "uq_income_sources_workspace_active_name";
    private static final String FALLBACK_ZONE = "Asia/Ho_Chi_Minh";

    private final IncomeSourceRepository incomeSourceRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    public IncomeSourceService(
            IncomeSourceRepository incomeSourceRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            UserRepository userRepository,
            Clock clock) {
        this.incomeSourceRepository = incomeSourceRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
        this.clock = clock;
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

    @Transactional(readOnly = true)
    public IncomeSourceSummaryResponse summary(UUID workspaceId, UUID incomeSourceId, String from, String to, UUID userId) {
        WorkspaceMember member = requireActiveMember(workspaceId, userId);
        IncomeSource source = findInWorkspace(workspaceId, incomeSourceId);
        Period period = resolvePeriod(member.getWorkspace(), from, to);
        SummaryTotals totals = aggregateForSources(workspaceId, List.of(incomeSourceId), period, currency(member.getWorkspace()))
                .getOrDefault(incomeSourceId, SummaryTotals.zero());
        return mapToSummary(source, period, totals, currency(member.getWorkspace()));
    }

    @Transactional(readOnly = true)
    public IncomeSourceSummaryListResponse summaries(UUID workspaceId, IncomeSourceStatus status, String search, String from, String to, UUID userId) {
        WorkspaceMember member = requireActiveMember(workspaceId, userId);
        IncomeSourceStatus effectiveStatus = status == null ? IncomeSourceStatus.ACTIVE : status;
        String pattern = searchPattern(search);
        List<IncomeSource> sources = pattern == null
                ? incomeSourceRepository.findAllForList(workspaceId, effectiveStatus)
                : incomeSourceRepository.searchForList(workspaceId, effectiveStatus, pattern);
        Period period = resolvePeriod(member.getWorkspace(), from, to);
        String currency = currency(member.getWorkspace());
        Map<UUID, SummaryTotals> totals = aggregateForSources(workspaceId, sources.stream().map(IncomeSource::getId).toList(), period, currency);
        return IncomeSourceSummaryListResponse.builder()
                .from(period.from())
                .toExclusive(period.toExclusive())
                .items(sources.stream()
                        .map(source -> mapToSummaryItem(source, totals.getOrDefault(source.getId(), SummaryTotals.zero()), currency))
                        .toList())
                .build();
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

    private Period resolvePeriod(Workspace workspace, String from, String to) {
        boolean hasFrom = from != null && !from.isBlank();
        boolean hasTo = to != null && !to.isBlank();
        if (hasFrom != hasTo) {
            throw new BusinessException("VALIDATION_ERROR", "Both from and to are required");
        }
        if (!hasFrom) {
            YearMonth currentMonth = YearMonth.from(today(workspace));
            return new Period(currentMonth.atDay(1), currentMonth.plusMonths(1).atDay(1));
        }
        try {
            LocalDate fromDate = LocalDate.parse(from.trim());
            LocalDate toDate = LocalDate.parse(to.trim());
            if (!fromDate.isBefore(toDate)) {
                throw new BusinessException("VALIDATION_ERROR", "from must be before to");
            }
            return new Period(fromDate, toDate);
        } catch (DateTimeParseException ex) {
            throw new BusinessException("VALIDATION_ERROR", "Date range is invalid");
        }
    }

    private Map<UUID, SummaryTotals> aggregateForSources(UUID workspaceId, List<UUID> sourceIds, Period period, String workspaceCurrency) {
        if (sourceIds.isEmpty()) {
            return Map.of();
        }
        ensureSingleCurrency(workspaceId, sourceIds, period, workspaceCurrency);
        List<Object[]> rows = entityManager.createQuery("""
                SELECT COALESCE(t.incomeSource.id, t.relatedIncomeSource.id),
                       COALESCE(SUM(CASE WHEN t.transactionType = :income THEN t.amount ELSE 0 END), 0),
                       COALESCE(SUM(CASE WHEN t.transactionType = :expense THEN t.amount ELSE 0 END), 0),
                       COALESCE(SUM(CASE WHEN t.transactionType = :income THEN 1 ELSE 0 END), 0),
                       COALESCE(SUM(CASE WHEN t.transactionType = :expense THEN 1 ELSE 0 END), 0)
                FROM Transaction t
                WHERE t.workspace.id = :workspaceId
                  AND t.transactionStatus = com.moneyflowbackend.transaction.model.TransactionStatus.POSTED
                  AND t.deletedAt IS NULL
                  AND t.transactionDate >= :from
                  AND t.transactionDate < :to
                  AND (
                    (t.transactionType = :income AND t.incomeSource.id IN :sourceIds)
                    OR (t.transactionType = :expense AND t.relatedIncomeSource.id IN :sourceIds)
                  )
                GROUP BY COALESCE(t.incomeSource.id, t.relatedIncomeSource.id)
                """, Object[].class)
                .setParameter("workspaceId", workspaceId)
                .setParameter("from", period.from())
                .setParameter("to", period.toExclusive())
                .setParameter("income", TransactionType.INCOME)
                .setParameter("expense", TransactionType.EXPENSE)
                .setParameter("sourceIds", sourceIds)
                .getResultList();
        Map<UUID, SummaryTotals> totals = new HashMap<>();
        for (Object[] row : rows) {
            BigDecimal gross = (BigDecimal) row[1];
            BigDecimal expenses = (BigDecimal) row[2];
            totals.put((UUID) row[0], new SummaryTotals(gross, expenses, longValue(row[3]), longValue(row[4])));
        }
        return totals;
    }

    private void ensureSingleCurrency(UUID workspaceId, Collection<UUID> sourceIds, Period period, String workspaceCurrency) {
        Long mixedCurrencyCount = entityManager.createQuery("""
                SELECT COUNT(t)
                FROM Transaction t
                WHERE t.workspace.id = :workspaceId
                  AND t.transactionStatus = com.moneyflowbackend.transaction.model.TransactionStatus.POSTED
                  AND t.deletedAt IS NULL
                  AND t.transactionDate >= :from
                  AND t.transactionDate < :to
                  AND t.currency <> :currency
                  AND (
                    (t.transactionType = :income AND t.incomeSource.id IN :sourceIds)
                    OR (t.transactionType = :expense AND t.relatedIncomeSource.id IN :sourceIds)
                  )
                """, Long.class)
                .setParameter("workspaceId", workspaceId)
                .setParameter("from", period.from())
                .setParameter("to", period.toExclusive())
                .setParameter("currency", workspaceCurrency)
                .setParameter("income", TransactionType.INCOME)
                .setParameter("expense", TransactionType.EXPENSE)
                .setParameter("sourceIds", sourceIds)
                .getSingleResult();
        if (mixedCurrencyCount > 0) {
            throw new BusinessException("INVALID_CURRENCY_AGGREGATION", "Income source summary has mixed currencies");
        }
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

    private IncomeSourceSummaryResponse mapToSummary(IncomeSource source, Period period, SummaryTotals totals, String currency) {
        return IncomeSourceSummaryResponse.builder()
                .incomeSourceId(source.getId())
                .name(source.getName())
                .type(source.getType())
                .status(source.getStatus())
                .from(period.from())
                .toExclusive(period.toExclusive())
                .grossIncome(totals.grossIncome())
                .directExpenses(totals.directExpenses())
                .netIncome(totals.netIncome())
                .incomeTransactionCount(totals.incomeTransactionCount())
                .expenseTransactionCount(totals.expenseTransactionCount())
                .currency(currency)
                .build();
    }

    private IncomeSourceSummaryItemResponse mapToSummaryItem(IncomeSource source, SummaryTotals totals, String currency) {
        return IncomeSourceSummaryItemResponse.builder()
                .incomeSourceId(source.getId())
                .name(source.getName())
                .type(source.getType())
                .status(source.getStatus())
                .grossIncome(totals.grossIncome())
                .directExpenses(totals.directExpenses())
                .netIncome(totals.netIncome())
                .incomeTransactionCount(totals.incomeTransactionCount())
                .expenseTransactionCount(totals.expenseTransactionCount())
                .currency(currency)
                .build();
    }

    private LocalDate today(Workspace workspace) {
        return LocalDate.now(clock.withZone(zone(workspace.getTimezone())));
    }

    private ZoneId zone(String timezone) {
        try {
            return ZoneId.of(timezone == null || timezone.isBlank() ? FALLBACK_ZONE : timezone.trim());
        } catch (Exception ex) {
            return ZoneId.of(FALLBACK_ZONE);
        }
    }

    private String currency(Workspace workspace) {
        return workspace.getCurrency() == null || workspace.getCurrency().isBlank() ? "VND" : workspace.getCurrency().trim();
    }

    private long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }

    private record ValidatedRequest(String name, IncomeSourceType type, String description) {
    }

    private record Period(LocalDate from, LocalDate toExclusive) {
    }

    private record SummaryTotals(BigDecimal grossIncome, BigDecimal directExpenses, long incomeTransactionCount, long expenseTransactionCount) {
        private static SummaryTotals zero() {
            return new SummaryTotals(BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
        }

        private BigDecimal netIncome() {
            return grossIncome.subtract(directExpenses);
        }
    }
}
