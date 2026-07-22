package com.moneyflowbackend.obligation.service;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.common.model.SpendingScope;
import com.moneyflowbackend.obligation.dto.RecurringObligationPreviewRequest;
import com.moneyflowbackend.obligation.dto.RecurringObligationPreviewResponse;
import com.moneyflowbackend.obligation.dto.RecurringObligationTemplatePageResponse;
import com.moneyflowbackend.obligation.dto.RecurringObligationTemplateRequest;
import com.moneyflowbackend.obligation.dto.RecurringObligationTemplateResponse;
import com.moneyflowbackend.obligation.model.ObligationAmountMode;
import com.moneyflowbackend.obligation.model.ObligationDirection;
import com.moneyflowbackend.obligation.model.ObligationFrequency;
import com.moneyflowbackend.obligation.model.RecurringObligationStatus;
import com.moneyflowbackend.obligation.model.RecurringObligationTemplate;
import com.moneyflowbackend.obligation.repository.ObligationOccurrenceRepository;
import com.moneyflowbackend.obligation.repository.RecurringObligationTemplateRepository;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.repository.WalletRepository;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class RecurringObligationTemplateService {
    private static final String FALLBACK_ZONE = "Asia/Ho_Chi_Minh";
    private static final int MAX_NAME_LENGTH = 160;
    private static final int MAX_INTERVAL_COUNT = 1200;
    private static final int MAX_REMINDER_DAYS = 3650;
    private static final int DEFAULT_PREVIEW_COUNT = 6;
    private static final int MAX_PREVIEW_COUNT = 24;

    private final RecurringObligationTemplateRepository templateRepository;
    private final ObligationOccurrenceRepository occurrenceRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final CategoryRepository categoryRepository;
    private final RecurrenceCalculator recurrenceCalculator;
    private final Clock clock;

    public RecurringObligationTemplateService(
            RecurringObligationTemplateRepository templateRepository,
            ObligationOccurrenceRepository occurrenceRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            UserRepository userRepository,
            WalletRepository walletRepository,
            CategoryRepository categoryRepository,
            RecurrenceCalculator recurrenceCalculator,
            Clock clock) {
        this.templateRepository = templateRepository;
        this.occurrenceRepository = occurrenceRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.categoryRepository = categoryRepository;
        this.recurrenceCalculator = recurrenceCalculator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RecurringObligationTemplatePageResponse list(
            UUID workspaceId,
            RecurringObligationStatus status,
            ObligationDirection direction,
            String search,
            boolean includeArchived,
            int page,
            int size,
            UUID userId) {
        WorkspaceMember member = requireActiveMember(workspaceId, userId);
        int pageNumber = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), 100);
        Page<RecurringObligationTemplate> result = templateRepository.findTemplatePage(
                workspaceId,
                statuses(status, includeArchived),
                direction,
                searchPattern(search),
                PageRequest.of(pageNumber, pageSize, Sort.by(
                        Sort.Order.asc("status"),
                        Sort.Order.asc("name"),
                        Sort.Order.asc("createdAt"),
                        Sort.Order.asc("id"))));
        Map<UUID, Boolean> occurrenceFlags = occurrenceFlags(result.getContent());
        List<RecurringObligationTemplateResponse> content = result.getContent().stream()
                .map(template -> mapToResponse(template, member.getWorkspace(), occurrenceFlags.getOrDefault(template.getId(), false)))
                .toList();
        return RecurringObligationTemplatePageResponse.builder()
                .content(content)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .first(result.isFirst())
                .last(result.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public RecurringObligationTemplateResponse get(UUID workspaceId, UUID templateId, UUID userId) {
        WorkspaceMember member = requireActiveMember(workspaceId, userId);
        RecurringObligationTemplate template = findTemplate(workspaceId, templateId);
        return mapToResponse(template, member.getWorkspace(), occurrenceRepository.existsByTemplateId(templateId));
    }

    @Transactional
    public RecurringObligationTemplateResponse create(UUID workspaceId, RecurringObligationTemplateRequest req, UUID userId) {
        WorkspaceMember member = requireWritableMember(workspaceId, userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));
        ValidatedRequest validated = validateRequest(workspaceId, req, false);
        RecurringObligationTemplate template = RecurringObligationTemplate.builder()
                .workspace(member.getWorkspace())
                .name(validated.name())
                .direction(validated.direction())
                .amountMode(validated.amountMode())
                .defaultAmount(validated.defaultAmount())
                .frequency(validated.frequency())
                .intervalCount(validated.intervalCount())
                .startDate(validated.startDate())
                .endDate(validated.endDate())
                .reminderDaysBefore(validated.reminderDaysBefore())
                .defaultWallet(validated.wallet())
                .defaultCategory(validated.category())
                .spendingScope(validated.spendingScope())
                .note(validated.note())
                .status(RecurringObligationStatus.ACTIVE)
                .createdByUser(user)
                .build();
        template = templateRepository.save(template);
        return mapToResponse(template, member.getWorkspace(), false);
    }

    @Transactional
    public RecurringObligationTemplateResponse update(UUID workspaceId, UUID templateId, RecurringObligationTemplateRequest req, UUID userId) {
        WorkspaceMember member = requireWritableMember(workspaceId, userId);
        RecurringObligationTemplate template = templateRepository.findByIdAndWorkspaceIdForUpdate(templateId, workspaceId)
                .orElseThrow(() -> notFound());
        boolean hasOccurrences = occurrenceRepository.existsByTemplateId(templateId);
        if (hasOccurrences && scheduleChanged(template, req)) {
            throw new BusinessException(
                    "OBLIGATION_SCHEDULE_LOCKED",
                    "Archive this recurring obligation and create a new one to change its schedule identity.",
                    HttpStatus.CONFLICT);
        }
        ValidatedRequest validated = validateRequest(workspaceId, req, false);
        template.setName(validated.name());
        template.setAmountMode(validated.amountMode());
        template.setDefaultAmount(validated.defaultAmount());
        template.setEndDate(validated.endDate());
        template.setReminderDaysBefore(validated.reminderDaysBefore());
        template.setDefaultWallet(validated.wallet());
        template.setDefaultCategory(validated.category());
        template.setSpendingScope(validated.spendingScope());
        template.setNote(validated.note());
        if (!hasOccurrences) {
            template.setDirection(validated.direction());
            template.setFrequency(validated.frequency());
            template.setIntervalCount(validated.intervalCount());
            template.setStartDate(validated.startDate());
        }
        template.setUpdatedAt(Instant.now(clock));
        return mapToResponse(template, member.getWorkspace(), hasOccurrences);
    }

    @Transactional
    public RecurringObligationTemplateResponse pause(UUID workspaceId, UUID templateId, UUID userId) {
        return transition(workspaceId, templateId, userId, RecurringObligationStatus.PAUSED);
    }

    @Transactional
    public RecurringObligationTemplateResponse resume(UUID workspaceId, UUID templateId, UUID userId) {
        return transition(workspaceId, templateId, userId, RecurringObligationStatus.ACTIVE);
    }

    @Transactional
    public RecurringObligationTemplateResponse archive(UUID workspaceId, UUID templateId, UUID userId) {
        return transition(workspaceId, templateId, userId, RecurringObligationStatus.ARCHIVED);
    }

    @Transactional(readOnly = true)
    public RecurringObligationPreviewResponse preview(UUID workspaceId, RecurringObligationPreviewRequest req, UUID userId) {
        requireActiveMember(workspaceId, userId);
        ObligationFrequency frequency = requireFrequency(req == null ? null : req.getFrequency());
        int interval = requireInterval(req == null ? null : req.getIntervalCount());
        LocalDate startDate = requireStartDate(req == null ? null : req.getStartDate());
        LocalDate endDate = req == null ? null : req.getEndDate();
        validateDateRange(startDate, endDate);
        Integer reminderDays = req == null ? null : req.getReminderDaysBefore();
        if (reminderDays != null) {
            requireReminderDays(reminderDays);
        }
        int count = Math.min(Math.max(req == null || req.getCount() == null ? DEFAULT_PREVIEW_COUNT : req.getCount(), 1), MAX_PREVIEW_COUNT);
        LocalDate toDate = previewToDate(startDate, endDate, frequency, interval, count + 1);
        List<LocalDate> rawDates = recurrenceCalculator.calculate(startDate, endDate, frequency, interval, startDate, toDate);
        boolean hasMore = rawDates.size() > count;
        List<LocalDate> dueDates = rawDates.stream().limit(count).toList();
        return RecurringObligationPreviewResponse.builder()
                .dueDates(dueDates)
                .reminderDates(reminderDays == null ? null : dueDates.stream().map(date -> date.minusDays(reminderDays)).toList())
                .hasMore(hasMore)
                .build();
    }

    private RecurringObligationTemplateResponse transition(
            UUID workspaceId,
            UUID templateId,
            UUID userId,
            RecurringObligationStatus targetStatus) {
        WorkspaceMember member = requireWritableMember(workspaceId, userId);
        RecurringObligationTemplate template = templateRepository.findByIdAndWorkspaceIdForUpdate(templateId, workspaceId)
                .orElseThrow(() -> notFound());
        if (template.getStatus() == RecurringObligationStatus.ARCHIVED && targetStatus != RecurringObligationStatus.ARCHIVED) {
            throw new BusinessException("INVALID_OBLIGATION_STATE", "Archived recurring obligations cannot be paused or resumed.", HttpStatus.CONFLICT);
        }
        if (template.getStatus() != targetStatus) {
            template.setStatus(targetStatus);
            template.setUpdatedAt(Instant.now(clock));
        }
        return mapToResponse(template, member.getWorkspace(), occurrenceRepository.existsByTemplateId(templateId));
    }

    private ValidatedRequest validateRequest(UUID workspaceId, RecurringObligationTemplateRequest req, boolean allowInactiveReferences) {
        if (req == null) {
            throw new BusinessException("VALIDATION_ERROR", "Request body is required");
        }
        String name = normalizeName(req.getName());
        ObligationDirection direction = requireDirection(req.getDirection());
        ObligationAmountMode amountMode = requireAmountMode(req.getAmountMode());
        BigDecimal defaultAmount = validateAmount(amountMode, req.getDefaultAmount());
        ObligationFrequency frequency = requireFrequency(req.getFrequency());
        int interval = requireInterval(req.getIntervalCount());
        LocalDate startDate = requireStartDate(req.getStartDate());
        LocalDate endDate = req.getEndDate();
        validateDateRange(startDate, endDate);
        int reminderDays = requireReminderDays(req.getReminderDaysBefore());
        Wallet wallet = resolveWallet(workspaceId, req.getDefaultWalletId(), allowInactiveReferences);
        Category category = resolveCategory(workspaceId, req.getDefaultCategoryId(), direction, allowInactiveReferences);
        SpendingScope spendingScope = validateSpendingScope(direction, req.getSpendingScope());
        return new ValidatedRequest(name, direction, amountMode, defaultAmount, frequency, interval, startDate, endDate,
                reminderDays, wallet, category, spendingScope, normalizeText(req.getNote()));
    }

    private String normalizeName(String name) {
        String value = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (value.isBlank() || value.length() > MAX_NAME_LENGTH) {
            throw new BusinessException("INVALID_OBLIGATION_NAME", "Recurring obligation name is invalid");
        }
        return value;
    }

    private ObligationDirection requireDirection(ObligationDirection direction) {
        if (direction == null) {
            throw new BusinessException("INVALID_OBLIGATION_DIRECTION", "Direction is required");
        }
        return direction;
    }

    private ObligationAmountMode requireAmountMode(ObligationAmountMode amountMode) {
        if (amountMode == null) {
            throw new BusinessException("INVALID_OBLIGATION_AMOUNT", "Amount mode is required");
        }
        return amountMode;
    }

    private BigDecimal validateAmount(ObligationAmountMode amountMode, BigDecimal amount) {
        if (amountMode == ObligationAmountMode.FIXED && amount == null) {
            throw new BusinessException("INVALID_OBLIGATION_AMOUNT", "Fixed recurring obligations require a default amount");
        }
        if (amount != null && amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_OBLIGATION_AMOUNT", "Default amount must be positive");
        }
        return amount;
    }

    private ObligationFrequency requireFrequency(ObligationFrequency frequency) {
        if (frequency == null) {
            throw new BusinessException("INVALID_RECURRENCE", "Frequency is required");
        }
        return frequency;
    }

    private int requireInterval(Integer intervalCount) {
        int value = intervalCount == null ? 1 : intervalCount;
        if (value < 1 || value > MAX_INTERVAL_COUNT) {
            throw new BusinessException("INVALID_RECURRENCE", "Interval count must be between 1 and " + MAX_INTERVAL_COUNT);
        }
        return value;
    }

    private LocalDate requireStartDate(LocalDate startDate) {
        if (startDate == null) {
            throw new BusinessException("INVALID_DATE_RANGE", "Start date is required");
        }
        return startDate;
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException("INVALID_DATE_RANGE", "End date must be on or after start date");
        }
    }

    private int requireReminderDays(Integer reminderDaysBefore) {
        int value = reminderDaysBefore == null ? 0 : reminderDaysBefore;
        if (value < 0 || value > MAX_REMINDER_DAYS) {
            throw new BusinessException("INVALID_RECURRENCE", "Reminder days must be between 0 and " + MAX_REMINDER_DAYS);
        }
        return value;
    }

    private Wallet resolveWallet(UUID workspaceId, UUID walletId, boolean allowInactive) {
        if (walletId == null) {
            return null;
        }
        Wallet wallet = walletRepository.findByIdAndWorkspaceId(walletId, workspaceId)
                .orElseThrow(() -> new BusinessException("WALLET_NOT_FOUND", "Wallet not found", HttpStatus.NOT_FOUND));
        if (!allowInactive && !wallet.isActive()) {
            throw new BusinessException("WALLET_INACTIVE", "Wallet is inactive");
        }
        return wallet;
    }

    private Category resolveCategory(UUID workspaceId, UUID categoryId, ObligationDirection direction, boolean allowInactive) {
        if (categoryId == null) {
            return null;
        }
        Category category = categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)
                .orElseThrow(() -> new BusinessException("CATEGORY_NOT_FOUND", "Category not found", HttpStatus.NOT_FOUND));
        if (!allowInactive) {
            if (!category.isActive()) {
                throw new BusinessException("CATEGORY_INACTIVE", "Category is inactive");
            }
            if (category.isArchived()) {
                throw new BusinessException("CATEGORY_ARCHIVED", "Category is archived");
            }
        }
        CategoryType expected = direction == ObligationDirection.PAYABLE ? CategoryType.EXPENSE : CategoryType.INCOME;
        if (category.getCategoryType() != expected) {
            throw new BusinessException("CATEGORY_TYPE_MISMATCH", "Category type mismatch");
        }
        return category;
    }

    private SpendingScope validateSpendingScope(ObligationDirection direction, SpendingScope spendingScope) {
        if (spendingScope != null && direction != ObligationDirection.PAYABLE) {
            throw new BusinessException(
                    "INVALID_OBLIGATION_SPENDING_SCOPE",
                    "Spending scope is only supported for payable obligations.");
        }
        return spendingScope;
    }

    private RecurringObligationTemplate findTemplate(UUID workspaceId, UUID templateId) {
        return templateRepository.findByIdAndWorkspaceIdWithReferences(templateId, workspaceId)
                .orElseThrow(() -> notFound());
    }

    private BusinessException notFound() {
        return new BusinessException("RECURRING_OBLIGATION_NOT_FOUND", "Recurring obligation not found", HttpStatus.NOT_FOUND);
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
            throw new BusinessException("FORBIDDEN", "Viewer cannot modify recurring obligations", HttpStatus.FORBIDDEN);
        }
        return member;
    }

    private List<RecurringObligationStatus> statuses(RecurringObligationStatus status, boolean includeArchived) {
        if (status != null) {
            return List.of(status);
        }
        return includeArchived
                ? List.of(RecurringObligationStatus.ACTIVE, RecurringObligationStatus.PAUSED, RecurringObligationStatus.ARCHIVED)
                : List.of(RecurringObligationStatus.ACTIVE, RecurringObligationStatus.PAUSED);
    }

    private String searchPattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private Map<UUID, Boolean> occurrenceFlags(List<RecurringObligationTemplate> templates) {
        if (templates.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Boolean> flags = new HashMap<>();
        occurrenceRepository.countByTemplateIds(templates.stream().map(RecurringObligationTemplate::getId).toList())
                .forEach(row -> flags.put((UUID) row[0], ((Long) row[1]) > 0));
        return flags;
    }

    private boolean scheduleChanged(RecurringObligationTemplate template, RecurringObligationTemplateRequest req) {
        if (req == null) {
            throw new BusinessException("VALIDATION_ERROR", "Request body is required");
        }
        return template.getDirection() != requireDirection(req.getDirection())
                || template.getFrequency() != requireFrequency(req.getFrequency())
                || !template.getIntervalCount().equals(requireInterval(req.getIntervalCount()))
                || !template.getStartDate().equals(requireStartDate(req.getStartDate()));
    }

    private RecurringObligationTemplateResponse mapToResponse(RecurringObligationTemplate template, Workspace workspace, boolean hasOccurrences) {
        return RecurringObligationTemplateResponse.builder()
                .id(template.getId())
                .workspaceId(workspace.getId())
                .name(template.getName())
                .direction(template.getDirection())
                .amountMode(template.getAmountMode())
                .defaultAmount(template.getDefaultAmount())
                .frequency(template.getFrequency())
                .intervalCount(template.getIntervalCount())
                .startDate(template.getStartDate())
                .endDate(template.getEndDate())
                .reminderDaysBefore(template.getReminderDaysBefore())
                .defaultWallet(walletSummary(template.getDefaultWallet()))
                .defaultCategory(categorySummary(template.getDefaultCategory()))
                .spendingScope(template.getSpendingScope())
                .note(template.getNote())
                .status(template.getStatus())
                .nextDueDate(nextDueDate(template, workspace))
                .hasOccurrences(hasOccurrences)
                .createdByUserId(template.getCreatedByUser().getId())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .version(template.getVersion())
                .build();
    }

    private RecurringObligationTemplateResponse.ReferenceSummary walletSummary(Wallet wallet) {
        if (wallet == null) {
            return null;
        }
        return RecurringObligationTemplateResponse.ReferenceSummary.builder()
                .id(wallet.getId())
                .name(wallet.getName())
                .type(wallet.getWalletType().name())
                .active(wallet.isActive())
                .build();
    }

    private RecurringObligationTemplateResponse.ReferenceSummary categorySummary(Category category) {
        if (category == null) {
            return null;
        }
        return RecurringObligationTemplateResponse.ReferenceSummary.builder()
                .id(category.getId())
                .name(category.getName())
                .type(category.getCategoryType().name())
                .active(category.isActive())
                .archived(category.isArchived())
                .build();
    }

    private LocalDate nextDueDate(RecurringObligationTemplate template, Workspace workspace) {
        if (template.getStatus() == RecurringObligationStatus.ARCHIVED) {
            return null;
        }
        LocalDate today = LocalDate.now(clock.withZone(zone(workspace)));
        LocalDate toDate = template.getEndDate() == null ? today.plusYears(200) : template.getEndDate();
        if (toDate.isBefore(today)) {
            return null;
        }
        return recurrenceCalculator.calculate(
                        template.getStartDate(),
                        template.getEndDate(),
                        template.getFrequency(),
                        template.getIntervalCount(),
                        today,
                        toDate)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private ZoneId zone(Workspace workspace) {
        try {
            return ZoneId.of(workspace.getTimezone() == null ? FALLBACK_ZONE : workspace.getTimezone());
        } catch (DateTimeException ex) {
            return ZoneId.of(FALLBACK_ZONE);
        }
    }

    private LocalDate previewToDate(LocalDate startDate, LocalDate endDate, ObligationFrequency frequency, int interval, int count) {
        LocalDate date = switch (frequency) {
            case WEEKLY -> startDate.plusWeeks((long) interval * count);
            case MONTHLY -> startDate.plusMonths((long) interval * count);
            case YEARLY -> startDate.plusYears((long) interval * count);
        };
        return endDate != null && endDate.isBefore(date) ? endDate : date;
    }

    private String normalizeText(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        return value.isEmpty() ? null : value;
    }

    private record ValidatedRequest(
            String name,
            ObligationDirection direction,
            ObligationAmountMode amountMode,
            BigDecimal defaultAmount,
            ObligationFrequency frequency,
            Integer intervalCount,
            LocalDate startDate,
            LocalDate endDate,
            Integer reminderDaysBefore,
            Wallet wallet,
            Category category,
            SpendingScope spendingScope,
            String note) {
    }
}
