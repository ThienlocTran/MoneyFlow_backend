package com.moneyflowbackend.categoryboard.service;

import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.categoryboard.dto.BoardPeriodResponse;
import com.moneyflowbackend.categoryboard.dto.BoardStatsResponse;
import com.moneyflowbackend.categoryboard.dto.CategoryBoardItemResponse;
import com.moneyflowbackend.categoryboard.dto.CategoryBoardResponse;
import com.moneyflowbackend.categoryboard.dto.CategoryStatsResponse;
import com.moneyflowbackend.categoryboard.dto.JarBoardGroupResponse;
import com.moneyflowbackend.categoryboard.dto.JarStatsResponse;
import com.moneyflowbackend.categoryboard.dto.UncategorizedGroupResponse;
import com.moneyflowbackend.categoryboard.dto.UncategorizedStatsResponse;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.jar.model.Jar;
import com.moneyflowbackend.jar.repository.JarRepository;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.workspace.service.WorkspaceService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CategoryBoardService {
    private static final String UNCATEGORIZED = "UNCATEGORIZED";
    private static final String UNCATEGORIZED_NAME = "Chưa gắn hũ";
    private static final String CURRENCY = "VND";
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final JarRepository jarRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final WorkspaceService workspaceService;
    private final Clock clock;

    public CategoryBoardService(
            JarRepository jarRepository,
            CategoryRepository categoryRepository,
            TransactionRepository transactionRepository,
            WorkspaceService workspaceService,
            Clock clock) {
        this.jarRepository = jarRepository;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.workspaceService = workspaceService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CategoryBoardResponse getBoard(
            UUID workspaceId,
            boolean includeArchived,
            boolean includeEmptyJars,
            boolean includeUncategorized,
            boolean includeStats,
            String from,
            String to,
            String period,
            UUID userId) {
        workspaceService.verifyMembership(workspaceId, userId);
        PeriodRange range = includeStats ? range(from, to, period) : null;

        List<Jar> jars = includeArchived
                ? jarRepository.findAllByWorkspaceIdOrderByDisplayOrderAscNameAsc(workspaceId)
                : jarRepository.findAllByWorkspaceIdAndIsActiveTrueOrderByDisplayOrderAscNameAsc(workspaceId);
        Map<UUID, Jar> visibleJars = new LinkedHashMap<>();
        for (Jar jar : jars.stream().sorted(this::compareJars).toList()) {
            visibleJars.put(jar.getId(), jar);
        }

        List<Category> categories = categoryRepository.findList(
                workspaceId, null, null, null, null, null, includeArchived, includeArchived)
                .stream()
                .sorted(this::compareCategories)
                .toList();

        Map<UUID, List<CategoryBoardItemResponse>> grouped = new LinkedHashMap<>();
        List<CategoryBoardItemResponse> uncategorized = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        warnings.add("HISTORICAL_JAR_SNAPSHOT_UNAVAILABLE");
        StatsContext stats = includeStats ? stats(workspaceId, range) : StatsContext.empty();

        for (Category category : categories) {
            Jar jar = category.getJar();
            if (jar != null && visibleJars.containsKey(jar.getId())) {
                grouped.computeIfAbsent(jar.getId(), ignored -> new ArrayList<>()).add(item(category, jar, stats));
            } else {
                if (jar != null) {
                    addOnce(warnings, "CATEGORY_BOARD_PARTIAL_DATA");
                }
                uncategorized.add(item(category, null, stats));
            }
        }
        if (!uncategorized.isEmpty()) {
            addOnce(warnings, "CATEGORY_UNCATEGORIZED_EXISTS");
        }

        List<JarBoardGroupResponse> groups = new ArrayList<>();
        for (Jar jar : visibleJars.values()) {
            List<CategoryBoardItemResponse> items = grouped.getOrDefault(jar.getId(), List.of());
            if (includeEmptyJars || !items.isEmpty()) {
                groups.add(new JarBoardGroupResponse(
                        jar.getId(),
                        jar.getName(),
                        null,
                        null,
                        null,
                        jar.getDisplayOrder(),
                        jar.isActive() ? "ACTIVE" : "INACTIVE",
                        items.size(),
                        includeStats ? jarStats(items, stats.totalExpense()) : null,
                        items));
            }
        }

        UncategorizedGroupResponse uncategorizedGroup = includeUncategorized
                ? new UncategorizedGroupResponse(
                        UNCATEGORIZED,
                        UNCATEGORIZED_NAME,
                        uncategorized.size(),
                        includeStats ? uncategorizedStats(uncategorized, stats.nullCategory(), stats.totalExpense()) : null,
                        uncategorized)
                : null;

        return new CategoryBoardResponse(
                workspaceId,
                Instant.now(),
                includeArchived,
                includeEmptyJars,
                includeUncategorized,
                range == null ? null : new BoardPeriodResponse(range.from(), range.to(), range.label()),
                includeStats ? boardStats(uncategorized, stats) : null,
                groups,
                uncategorizedGroup,
                warnings);
    }

    private CategoryBoardItemResponse item(Category category, Jar visibleJar, StatsContext stats) {
        boolean active = category.isActive() && !category.isArchived();
        return new CategoryBoardItemResponse(
                category.getId(),
                category.getName(),
                category.getCategoryType().name(),
                null,
                category.getIcon(),
                category.getDisplayOrder(),
                category.isArchived() ? "ARCHIVED" : category.isActive() ? "ACTIVE" : "INACTIVE",
                visibleJar == null ? null : visibleJar.getId(),
                visibleJar == null ? null : visibleJar.getName(),
                active,
                active,
                false,
                stats.categoryStats().get(category.getId()),
                List.of());
    }

    private StatsContext stats(UUID workspaceId, PeriodRange range) {
        Map<UUID, CategoryStatsResponse> categoryStats = new HashMap<>();
        RawStats nullCategory = RawStats.zero(null);
        for (TransactionRepository.CategoryBoardStatsRow row : transactionRepository.categoryBoardStats(workspaceId, range.from(), range.to())) {
            RawStats raw = raw(row);
            if (row.getCategoryId() == null) {
                nullCategory = raw;
            } else {
                categoryStats.put(row.getCategoryId(), categoryStats(raw, BigDecimal.ZERO));
            }
        }
        BigDecimal totalExpense = categoryStats.values().stream()
                .map(CategoryStatsResponse::totalExpense)
                .reduce(nullCategory.totalExpense(), BigDecimal::add);
        Map<UUID, CategoryStatsResponse> withPercent = new HashMap<>();
        for (Map.Entry<UUID, CategoryStatsResponse> entry : categoryStats.entrySet()) {
            CategoryStatsResponse s = entry.getValue();
            withPercent.put(entry.getKey(), new CategoryStatsResponse(
                    s.totalExpense(),
                    s.totalIncome(),
                    s.transactionCount(),
                    s.expenseTransactionCount(),
                    s.incomeTransactionCount(),
                    percent(s.totalExpense(), totalExpense),
                    s.lastUsedAt(),
                    s.currency()));
        }
        return new StatsContext(withPercent, nullCategory, totalExpense);
    }

    private BoardStatsResponse boardStats(List<CategoryBoardItemResponse> uncategorized, StatsContext stats) {
        BigDecimal uncategorizedExpense = uncategorized.stream()
                .map(CategoryBoardItemResponse::stats)
                .filter(s -> s != null)
                .map(CategoryStatsResponse::totalExpense)
                .reduce(stats.nullCategory().totalExpense(), BigDecimal::add);
        long uncategorizedCount = uncategorized.stream()
                .map(CategoryBoardItemResponse::stats)
                .filter(s -> s != null)
                .mapToLong(CategoryStatsResponse::expenseTransactionCount)
                .sum() + stats.nullCategory().expenseTransactionCount();
        BigDecimal totalIncome = stats.categoryStats().values().stream()
                .map(CategoryStatsResponse::totalIncome)
                .reduce(stats.nullCategory().totalIncome(), BigDecimal::add);
        long totalTransactions = stats.categoryStats().values().stream()
                .mapToLong(CategoryStatsResponse::transactionCount)
                .sum() + stats.nullCategory().transactionCount();
        return new BoardStatsResponse(
                stats.totalExpense(),
                totalIncome,
                totalTransactions,
                stats.totalExpense().subtract(uncategorizedExpense),
                uncategorizedExpense,
                uncategorizedCount,
                CURRENCY);
    }

    private JarStatsResponse jarStats(List<CategoryBoardItemResponse> items, BigDecimal totalExpense) {
        BigDecimal expense = BigDecimal.ZERO;
        BigDecimal income = BigDecimal.ZERO;
        long transactions = 0;
        long expenseCount = 0;
        long incomeCount = 0;
        long usedCategories = 0;
        LocalDate lastUsed = null;
        for (CategoryBoardItemResponse item : items) {
            CategoryStatsResponse stats = item.stats();
            if (stats == null) continue;
            expense = expense.add(stats.totalExpense());
            income = income.add(stats.totalIncome());
            transactions += stats.transactionCount();
            expenseCount += stats.expenseTransactionCount();
            incomeCount += stats.incomeTransactionCount();
            if (stats.transactionCount() > 0) usedCategories++;
            lastUsed = max(lastUsed, stats.lastUsedAt());
        }
        return new JarStatsResponse(
                expense,
                income,
                transactions,
                expenseCount,
                incomeCount,
                percent(expense, totalExpense),
                lastUsed,
                items.stream().filter(item -> "ACTIVE".equals(item.status())).count(),
                usedCategories,
                CURRENCY);
    }

    private UncategorizedStatsResponse uncategorizedStats(
            List<CategoryBoardItemResponse> items,
            RawStats nullCategory,
            BigDecimal totalExpense) {
        BigDecimal expense = items.stream()
                .map(CategoryBoardItemResponse::stats)
                .filter(s -> s != null)
                .map(CategoryStatsResponse::totalExpense)
                .reduce(nullCategory.totalExpense(), BigDecimal::add);
        long count = items.stream()
                .map(CategoryBoardItemResponse::stats)
                .filter(s -> s != null)
                .mapToLong(CategoryStatsResponse::expenseTransactionCount)
                .sum() + nullCategory.expenseTransactionCount();
        return new UncategorizedStatsResponse(expense, count, percent(expense, totalExpense), items.size(), CURRENCY);
    }

    private CategoryStatsResponse categoryStats(RawStats raw, BigDecimal totalExpense) {
        return new CategoryStatsResponse(
                raw.totalExpense(),
                raw.totalIncome(),
                raw.transactionCount(),
                raw.expenseTransactionCount(),
                raw.incomeTransactionCount(),
                percent(raw.totalExpense(), totalExpense),
                raw.lastUsedAt(),
                CURRENCY);
    }

    private RawStats raw(TransactionRepository.CategoryBoardStatsRow row) {
        return new RawStats(
                nz(row.getTotalExpense()),
                nz(row.getTotalIncome()),
                row.getTransactionCount(),
                row.getExpenseTransactionCount(),
                row.getIncomeTransactionCount(),
                row.getLastUsedAt());
    }

    private BigDecimal percent(BigDecimal amount, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(ONE_HUNDRED).divide(total, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private LocalDate max(LocalDate left, LocalDate right) {
        if (left == null) return right;
        if (right == null) return left;
        return left.isAfter(right) ? left : right;
    }

    private PeriodRange range(String rawFrom, String rawTo, String rawPeriod) {
        if ((rawFrom == null || rawFrom.isBlank()) && (rawTo == null || rawTo.isBlank())) {
            return period(rawPeriod);
        }
        LocalDate from = parseDate(rawFrom, "from");
        LocalDate to = parseDate(rawTo, "to");
        if (from.isAfter(to)) {
            throw new BusinessException("INVALID_DATE_RANGE", "from must be before or equal to to", HttpStatus.BAD_REQUEST);
        }
        return new PeriodRange(from, to, "custom");
    }

    private PeriodRange period(String rawPeriod) {
        LocalDate today = LocalDate.now(clock);
        String period = rawPeriod == null || rawPeriod.isBlank() ? "month" : rawPeriod.trim().toLowerCase();
        return switch (period) {
            case "today" -> new PeriodRange(today, today, "today");
            case "week" -> new PeriodRange(today.minusDays(6), today, "week");
            case "month" -> {
                YearMonth month = YearMonth.from(today);
                yield new PeriodRange(month.atDay(1), month.atEndOfMonth(), "month");
            }
            case "custom" -> throw new BusinessException("INVALID_DATE_RANGE", "from and to are required for custom period", HttpStatus.BAD_REQUEST);
            default -> throw new BusinessException("INVALID_PERIOD", "period must be today, week, month, or custom", HttpStatus.BAD_REQUEST);
        };
    }

    private LocalDate parseDate(String raw, String field) {
        try {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException();
            }
            return LocalDate.parse(raw.trim());
        } catch (Exception ex) {
            throw new BusinessException("INVALID_DATE_RANGE", field + " must use YYYY-MM-DD", HttpStatus.BAD_REQUEST);
        }
    }

    private int compareJars(Jar left, Jar right) {
        int order = compareNullable(left.getDisplayOrder(), right.getDisplayOrder());
        if (order != 0) return order;
        order = compareString(left.getName(), right.getName());
        if (order != 0) return order;
        order = compareNullable(left.getCreatedAt(), right.getCreatedAt());
        if (order != 0) return order;
        return left.getId().compareTo(right.getId());
    }

    private int compareCategories(Category left, Category right) {
        int order = compareNullable(left.getDisplayOrder(), right.getDisplayOrder());
        if (order != 0) return order;
        order = compareString(left.getName(), right.getName());
        if (order != 0) return order;
        order = compareNullable(left.getCreatedAt(), right.getCreatedAt());
        if (order != 0) return order;
        return left.getId().compareTo(right.getId());
    }

    private <T extends Comparable<T>> int compareNullable(T left, T right) {
        if (left == null && right == null) return 0;
        if (left == null) return 1;
        if (right == null) return -1;
        return left.compareTo(right);
    }

    private int compareString(String left, String right) {
        return Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER).compare(left, right);
    }

    private void addOnce(List<String> warnings, String warning) {
        if (!warnings.contains(warning)) {
            warnings.add(warning);
        }
    }

    private record PeriodRange(LocalDate from, LocalDate to, String label) {
    }

    private record RawStats(
            BigDecimal totalExpense,
            BigDecimal totalIncome,
            long transactionCount,
            long expenseTransactionCount,
            long incomeTransactionCount,
            LocalDate lastUsedAt) {
        static RawStats zero(LocalDate lastUsedAt) {
            return new RawStats(BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0, lastUsedAt);
        }
    }

    private record StatsContext(
            Map<UUID, CategoryStatsResponse> categoryStats,
            RawStats nullCategory,
            BigDecimal totalExpense) {
        static StatsContext empty() {
            return new StatsContext(Map.of(), RawStats.zero(null), BigDecimal.ZERO);
        }
    }
}
