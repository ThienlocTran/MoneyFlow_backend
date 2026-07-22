package com.moneyflowbackend;

import com.moneyflowbackend.auth.dto.LoginRequest;
import com.moneyflowbackend.auth.dto.RegisterRequest;
import com.moneyflowbackend.auth.dto.TokenResponse;
import com.moneyflowbackend.auth.dto.UserResponse;
import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.auth.service.AuthService;
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
import com.moneyflowbackend.obligation.model.ObligationOccurrence;
import com.moneyflowbackend.obligation.model.ObligationOccurrenceStatus;
import com.moneyflowbackend.obligation.model.RecurringObligationStatus;
import com.moneyflowbackend.obligation.model.RecurringObligationTemplate;
import com.moneyflowbackend.obligation.repository.ObligationOccurrenceRepository;
import com.moneyflowbackend.obligation.repository.RecurringObligationTemplateRepository;
import com.moneyflowbackend.obligation.service.RecurringObligationTemplateService;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.model.WalletType;
import com.moneyflowbackend.wallet.repository.WalletRepository;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RecurringObligationTemplateApiIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired RecurringObligationTemplateService templateService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired RecurringObligationTemplateRepository templateRepository;
    @Autowired ObligationOccurrenceRepository occurrenceRepository;
    @Autowired TransactionRepository transactionRepository;

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-07-20T01:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Test
    void createListGetAndAuthorizationRulesWork() {
        TestContext owner = createContext("ob_api_owner", WorkspaceRole.OWNER);
        TestContext editor = createContext("ob_api_editor", WorkspaceRole.OWNER);
        TestContext viewer = createContext("ob_api_viewer", WorkspaceRole.OWNER);
        TestContext outsider = createContext("ob_api_outsider", WorkspaceRole.OWNER);
        workspaceMemberRepository.saveAndFlush(WorkspaceMember.builder().workspace(owner.workspace()).user(editor.user()).role(WorkspaceRole.EDITOR).build());
        workspaceMemberRepository.saveAndFlush(WorkspaceMember.builder().workspace(owner.workspace()).user(viewer.user()).role(WorkspaceRole.VIEWER).build());
        Wallet wallet = wallet(owner, "Cash", true);
        Category expense = category(owner, "Rent", CategoryType.EXPENSE, true, false);
        Category income = category(owner, "Salary", CategoryType.INCOME, true, false);

        RecurringObligationTemplateRequest createRequest = request("  Rent   monthly  ", ObligationDirection.PAYABLE, ObligationAmountMode.FIXED, "3500000",
                ObligationFrequency.MONTHLY, 1, "2026-08-05", null, 3, wallet.getId(), expense.getId(), " note ");
        createRequest.setSpendingScope(SpendingScope.WORK);
        RecurringObligationTemplateResponse created = templateService.create(owner.workspace().getId(), createRequest, owner.user().getId());
        RecurringObligationTemplateResponse ended = templateService.create(owner.workspace().getId(),
                request("Ended", ObligationDirection.PAYABLE, ObligationAmountMode.FIXED, "1",
                        ObligationFrequency.MONTHLY, 1, "2026-06-01", "2026-06-30", 0, null, expense.getId(), null),
                owner.user().getId());
        RecurringObligationTemplateResponse editorCreated = templateService.create(owner.workspace().getId(),
                request("Salary", ObligationDirection.RECEIVABLE, ObligationAmountMode.VARIABLE, null,
                        ObligationFrequency.MONTHLY, 1, "2026-08-01", null, 0, null, income.getId(), null),
                editor.user().getId());
        templateService.pause(owner.workspace().getId(), editorCreated.getId(), owner.user().getId());
        templateService.archive(owner.workspace().getId(), editorCreated.getId(), owner.user().getId());

        assertThat(created.getName()).isEqualTo("Rent monthly");
        assertThat(created.getStatus()).isEqualTo(RecurringObligationStatus.ACTIVE);
        assertThat(created.getDefaultWallet().getName()).isEqualTo("Cash");
        assertThat(created.getDefaultCategory().getType()).isEqualTo("EXPENSE");
        assertThat(created.getSpendingScope()).isEqualTo(SpendingScope.WORK);
        assertThat(created.getNextDueDate()).isEqualTo(LocalDate.of(2026, 8, 5));
        assertThat(created.isHasOccurrences()).isFalse();
        assertThat(templateService.get(owner.workspace().getId(), ended.getId(), owner.user().getId()).getNextDueDate()).isNull();
        assertThat(templateService.get(owner.workspace().getId(), ended.getId(), owner.user().getId()).getSpendingScope()).isNull();
        assertThat(templateRepository.findById(created.getId()).orElseThrow().getStatus()).isEqualTo(RecurringObligationStatus.ACTIVE);

        RecurringObligationTemplatePageResponse defaultPage = templateService.list(
                owner.workspace().getId(), null, null, null, false, 0, 20, viewer.user().getId());
        assertThat(defaultPage.getContent()).extracting(RecurringObligationTemplateResponse::getId).containsExactly(ended.getId(), created.getId());
        assertThat(defaultPage.getContent()).extracting(RecurringObligationTemplateResponse::getSpendingScope)
                .containsExactly(null, SpendingScope.WORK);
        RecurringObligationTemplatePageResponse archivedPage = templateService.list(
                owner.workspace().getId(), RecurringObligationStatus.ARCHIVED, null, "salary", false, 0, 20, owner.user().getId());
        assertThat(archivedPage.getContent()).extracting(RecurringObligationTemplateResponse::getId).containsExactly(editorCreated.getId());
        assertThat(templateService.get(owner.workspace().getId(), editorCreated.getId(), viewer.user().getId()).getStatus())
                .isEqualTo(RecurringObligationStatus.ARCHIVED);

        assertBusinessCode(() -> templateService.create(owner.workspace().getId(),
                request("Viewer", ObligationDirection.PAYABLE, ObligationAmountMode.FIXED, "1",
                        ObligationFrequency.WEEKLY, 1, "2026-07-20", null, 0, wallet.getId(), expense.getId(), null),
                viewer.user().getId()), "FORBIDDEN");
        assertBusinessCode(() -> templateService.list(owner.workspace().getId(), null, null, null, false, 0, 20, outsider.user().getId()),
                "WORKSPACE_ACCESS_DENIED");
    }

    @Test
    void validationRejectsBadAmountsDatesReferencesAndCategoryDirection() {
        TestContext ctx = createContext("ob_api_validation", WorkspaceRole.OWNER);
        TestContext other = createContext("ob_api_other", WorkspaceRole.OWNER);
        Wallet wallet = wallet(ctx, "Cash", true);
        Wallet inactiveWallet = wallet(ctx, "Old cash", false);
        Wallet otherWallet = wallet(other, "Other cash", true);
        Category expense = category(ctx, "Food", CategoryType.EXPENSE, true, false);
        Category income = category(ctx, "Salary", CategoryType.INCOME, true, false);
        Category inactive = category(ctx, "Old", CategoryType.EXPENSE, false, false);
        Category archived = category(ctx, "Archived", CategoryType.EXPENSE, true, true);
        Category otherCategory = category(other, "Other", CategoryType.EXPENSE, true, false);

        assertBusinessCode(() -> templateService.create(ctx.workspace().getId(), request(" ", ObligationDirection.PAYABLE, ObligationAmountMode.FIXED, "1",
                ObligationFrequency.MONTHLY, 1, "2026-08-01", null, 0, wallet.getId(), expense.getId(), null), ctx.user().getId()), "INVALID_OBLIGATION_NAME");
        assertBusinessCode(() -> templateService.create(ctx.workspace().getId(), request("Bad", ObligationDirection.PAYABLE, ObligationAmountMode.FIXED, null,
                ObligationFrequency.MONTHLY, 1, "2026-08-01", null, 0, wallet.getId(), expense.getId(), null), ctx.user().getId()), "INVALID_OBLIGATION_AMOUNT");
        assertBusinessCode(() -> templateService.create(ctx.workspace().getId(), request("Bad", ObligationDirection.PAYABLE, ObligationAmountMode.FIXED, "0",
                ObligationFrequency.MONTHLY, 1, "2026-08-01", null, 0, wallet.getId(), expense.getId(), null), ctx.user().getId()), "INVALID_OBLIGATION_AMOUNT");
        assertBusinessCode(() -> templateService.create(ctx.workspace().getId(), request("Bad", ObligationDirection.PAYABLE, ObligationAmountMode.VARIABLE, "-1",
                ObligationFrequency.MONTHLY, 1, "2026-08-01", null, 0, wallet.getId(), expense.getId(), null), ctx.user().getId()), "INVALID_OBLIGATION_AMOUNT");
        assertThat(templateService.create(ctx.workspace().getId(), request("Variable", ObligationDirection.PAYABLE, ObligationAmountMode.VARIABLE, null,
                ObligationFrequency.MONTHLY, 1, "2026-08-01", null, 0, null, null, null), ctx.user().getId()).getDefaultAmount()).isNull();
        assertBusinessCode(() -> templateService.create(ctx.workspace().getId(), request("Bad", ObligationDirection.PAYABLE, ObligationAmountMode.FIXED, "1",
                ObligationFrequency.MONTHLY, 0, "2026-08-01", null, 0, wallet.getId(), expense.getId(), null), ctx.user().getId()), "INVALID_RECURRENCE");
        assertBusinessCode(() -> templateService.create(ctx.workspace().getId(), request("Bad", ObligationDirection.PAYABLE, ObligationAmountMode.FIXED, "1",
                ObligationFrequency.MONTHLY, 1, "2026-08-02", "2026-08-01", 0, wallet.getId(), expense.getId(), null), ctx.user().getId()), "INVALID_DATE_RANGE");
        assertBusinessCode(() -> templateService.create(ctx.workspace().getId(), request("Bad", ObligationDirection.PAYABLE, ObligationAmountMode.FIXED, "1",
                ObligationFrequency.MONTHLY, 1, "2026-08-01", null, 0, otherWallet.getId(), expense.getId(), null), ctx.user().getId()), "WALLET_NOT_FOUND");
        assertBusinessCode(() -> templateService.create(ctx.workspace().getId(), request("Bad", ObligationDirection.PAYABLE, ObligationAmountMode.FIXED, "1",
                ObligationFrequency.MONTHLY, 1, "2026-08-01", null, 0, inactiveWallet.getId(), expense.getId(), null), ctx.user().getId()), "WALLET_INACTIVE");
        assertBusinessCode(() -> templateService.create(ctx.workspace().getId(), request("Bad", ObligationDirection.PAYABLE, ObligationAmountMode.FIXED, "1",
                ObligationFrequency.MONTHLY, 1, "2026-08-01", null, 0, wallet.getId(), otherCategory.getId(), null), ctx.user().getId()), "CATEGORY_NOT_FOUND");
        assertBusinessCode(() -> templateService.create(ctx.workspace().getId(), request("Bad", ObligationDirection.PAYABLE, ObligationAmountMode.FIXED, "1",
                ObligationFrequency.MONTHLY, 1, "2026-08-01", null, 0, wallet.getId(), inactive.getId(), null), ctx.user().getId()), "CATEGORY_INACTIVE");
        assertBusinessCode(() -> templateService.create(ctx.workspace().getId(), request("Bad", ObligationDirection.PAYABLE, ObligationAmountMode.FIXED, "1",
                ObligationFrequency.MONTHLY, 1, "2026-08-01", null, 0, wallet.getId(), archived.getId(), null), ctx.user().getId()), "CATEGORY_ARCHIVED");
        assertBusinessCode(() -> templateService.create(ctx.workspace().getId(), request("Bad", ObligationDirection.PAYABLE, ObligationAmountMode.FIXED, "1",
                ObligationFrequency.MONTHLY, 1, "2026-08-01", null, 0, wallet.getId(), income.getId(), null), ctx.user().getId()), "CATEGORY_TYPE_MISMATCH");
        assertBusinessCode(() -> templateService.create(ctx.workspace().getId(), request("Bad", ObligationDirection.RECEIVABLE, ObligationAmountMode.FIXED, "1",
                ObligationFrequency.MONTHLY, 1, "2026-08-01", null, 0, wallet.getId(), expense.getId(), null), ctx.user().getId()), "CATEGORY_TYPE_MISMATCH");
        RecurringObligationTemplateRequest receivableScope = request("Bad scope", ObligationDirection.RECEIVABLE, ObligationAmountMode.FIXED, "1",
                ObligationFrequency.MONTHLY, 1, "2026-08-01", null, 0, wallet.getId(), income.getId(), null);
        receivableScope.setSpendingScope(SpendingScope.WORK);
        assertBusinessCode(() -> templateService.create(ctx.workspace().getId(), receivableScope, ctx.user().getId()), "INVALID_OBLIGATION_SPENDING_SCOPE");
    }

    @Test
    void updateLocksScheduleOnlyAfterOccurrencesExistAndDoesNotTouchOccurrenceOrTransactions() {
        TestContext ctx = createContext("ob_api_update", WorkspaceRole.OWNER);
        Wallet wallet = wallet(ctx, "Cash", true);
        Category expense = category(ctx, "Rent", CategoryType.EXPENSE, true, false);
        RecurringObligationTemplateResponse created = templateService.create(ctx.workspace().getId(),
                request("Rent", ObligationDirection.PAYABLE, ObligationAmountMode.FIXED, "100",
                        ObligationFrequency.MONTHLY, 1, "2026-08-05", null, 0, wallet.getId(), expense.getId(), null),
                ctx.user().getId());

        RecurringObligationTemplateResponse rescheduled = templateService.update(ctx.workspace().getId(), created.getId(),
                request("Rent", ObligationDirection.PAYABLE, ObligationAmountMode.FIXED, "100",
                        ObligationFrequency.WEEKLY, 2, "2026-08-06", null, 0, wallet.getId(), expense.getId(), null),
                ctx.user().getId());
        assertThat(rescheduled.getFrequency()).isEqualTo(ObligationFrequency.WEEKLY);

        RecurringObligationTemplate template = templateRepository.findById(created.getId()).orElseThrow();
        occurrenceRepository.saveAndFlush(ObligationOccurrence.builder()
                .workspace(ctx.workspace())
                .template(template)
                .periodKey("2026-08-06")
                .dueDate(LocalDate.of(2026, 8, 6))
                .expectedAmount(new BigDecimal("100"))
                .status(ObligationOccurrenceStatus.PENDING)
                .build());
        long transactionCount = transactionRepository.count();

        assertBusinessCode(() -> templateService.update(ctx.workspace().getId(), created.getId(),
                request("Rent", ObligationDirection.RECEIVABLE, ObligationAmountMode.FIXED, "100",
                        ObligationFrequency.WEEKLY, 2, "2026-08-06", null, 0, wallet.getId(), expense.getId(), null),
                ctx.user().getId()), "OBLIGATION_SCHEDULE_LOCKED");
        RecurringObligationTemplateResponse updated = templateService.update(ctx.workspace().getId(), created.getId(),
                request("Rent updated", ObligationDirection.PAYABLE, ObligationAmountMode.VARIABLE, "150",
                        ObligationFrequency.WEEKLY, 2, "2026-08-06", "2026-08-20", 5, wallet.getId(), expense.getId(), "future only"),
                ctx.user().getId());
        RecurringObligationTemplateRequest scopedUpdate = request("Rent scoped", ObligationDirection.PAYABLE, ObligationAmountMode.VARIABLE, "150",
                ObligationFrequency.WEEKLY, 2, "2026-08-06", "2026-08-20", 5, wallet.getId(), expense.getId(), "future only");
        scopedUpdate.setSpendingScope(SpendingScope.FAMILY);
        assertThat(templateService.update(ctx.workspace().getId(), created.getId(), scopedUpdate, ctx.user().getId()).getSpendingScope())
                .isEqualTo(SpendingScope.FAMILY);
        assertThat(templateService.update(ctx.workspace().getId(), created.getId(),
                request("Rent cleared", ObligationDirection.PAYABLE, ObligationAmountMode.VARIABLE, "150",
                        ObligationFrequency.WEEKLY, 2, "2026-08-06", "2026-08-20", 5, wallet.getId(), expense.getId(), "future only"),
                ctx.user().getId()).getSpendingScope()).isNull();

        ObligationOccurrence occurrence = occurrenceRepository.findByTemplateIdAndPeriodKey(created.getId(), "2026-08-06").orElseThrow();
        assertThat(updated.isHasOccurrences()).isTrue();
        assertThat(updated.getName()).isEqualTo("Rent updated");
        assertThat(updated.getAmountMode()).isEqualTo(ObligationAmountMode.VARIABLE);
        assertThat(occurrence.getExpectedAmount()).isEqualByComparingTo("100");
        assertThat(occurrence.getDueDate()).isEqualTo(LocalDate.of(2026, 8, 6));
        assertThat(transactionRepository.count()).isEqualTo(transactionCount);
    }

    @Test
    void pauseResumeArchiveAreIdempotentAndArchivedCannotReactivate() {
        TestContext ctx = createContext("ob_api_status", WorkspaceRole.OWNER);
        Wallet wallet = wallet(ctx, "Cash", true);
        Category expense = category(ctx, "Rent", CategoryType.EXPENSE, true, false);
        RecurringObligationTemplateResponse created = templateService.create(ctx.workspace().getId(),
                request("Rent", ObligationDirection.PAYABLE, ObligationAmountMode.FIXED, "100",
                        ObligationFrequency.MONTHLY, 1, "2026-08-05", null, 0, wallet.getId(), expense.getId(), null),
                ctx.user().getId());

        assertThat(templateService.pause(ctx.workspace().getId(), created.getId(), ctx.user().getId()).getStatus()).isEqualTo(RecurringObligationStatus.PAUSED);
        assertThat(templateService.pause(ctx.workspace().getId(), created.getId(), ctx.user().getId()).getStatus()).isEqualTo(RecurringObligationStatus.PAUSED);
        assertThat(templateService.resume(ctx.workspace().getId(), created.getId(), ctx.user().getId()).getStatus()).isEqualTo(RecurringObligationStatus.ACTIVE);
        assertThat(templateService.resume(ctx.workspace().getId(), created.getId(), ctx.user().getId()).getStatus()).isEqualTo(RecurringObligationStatus.ACTIVE);
        assertThat(templateService.archive(ctx.workspace().getId(), created.getId(), ctx.user().getId()).getStatus()).isEqualTo(RecurringObligationStatus.ARCHIVED);
        assertThat(templateService.archive(ctx.workspace().getId(), created.getId(), ctx.user().getId()).getStatus()).isEqualTo(RecurringObligationStatus.ARCHIVED);
        assertThat(templateService.get(ctx.workspace().getId(), created.getId(), ctx.user().getId()).getNextDueDate()).isNull();
        assertBusinessCode(() -> templateService.pause(ctx.workspace().getId(), created.getId(), ctx.user().getId()), "INVALID_OBLIGATION_STATE");
        assertBusinessCode(() -> templateService.resume(ctx.workspace().getId(), created.getId(), ctx.user().getId()), "INVALID_OBLIGATION_STATE");
    }

    @Test
    void previewUsesCalculatorLimitsCountAndDoesNotPersist() {
        TestContext ctx = createContext("ob_api_preview", WorkspaceRole.VIEWER);
        long templatesBefore = templateRepository.count();
        long occurrencesBefore = occurrenceRepository.count();

        RecurringObligationPreviewResponse monthly = templateService.preview(ctx.workspace().getId(),
                preview(ObligationFrequency.MONTHLY, 1, "2026-01-31", "2026-04-30", 2, 3),
                ctx.user().getId());
        RecurringObligationPreviewResponse yearly = templateService.preview(ctx.workspace().getId(),
                preview(ObligationFrequency.YEARLY, 1, "2024-02-29", "2028-12-31", null, 30),
                ctx.user().getId());

        assertThat(monthly.getDueDates()).containsExactly(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31));
        assertThat(monthly.getReminderDates()).containsExactly(LocalDate.of(2026, 1, 29), LocalDate.of(2026, 2, 26), LocalDate.of(2026, 3, 29));
        assertThat(monthly.isHasMore()).isTrue();
        assertThat(yearly.getDueDates()).contains(LocalDate.of(2024, 2, 29), LocalDate.of(2025, 2, 28));
        assertThat(yearly.getDueDates()).hasSizeLessThanOrEqualTo(24);
        assertThat(templateRepository.count()).isEqualTo(templatesBefore);
        assertThat(occurrenceRepository.count()).isEqualTo(occurrencesBefore);
    }

    @Test
    void controllerPathsReturnWrappedJsonAndDoNotGenerateOccurrences() throws Exception {
        String username = "ob_api_http_" + UUID.randomUUID().toString().substring(0, 8);
        UserResponse registered = authService.register(registerRequest(username));
        TokenResponse token = authService.login(loginRequest(username));
        Workspace workspace = workspaceRepository.findAllByUserId(registered.getId()).getFirst();
        Wallet wallet = walletRepository.saveAndFlush(Wallet.builder()
                .workspace(workspace)
                .name("Cash")
                .walletType(WalletType.CASH)
                .openingBalance(BigDecimal.ZERO)
                .build());
        Category category = categoryRepository.saveAndFlush(Category.builder()
                .workspace(workspace)
                .name("Rent")
                .categoryType(CategoryType.EXPENSE)
                .build());

        String body = """
                {
                  "name": "Rent",
                  "direction": "PAYABLE",
                  "amountMode": "FIXED",
                  "defaultAmount": 100,
                  "frequency": "MONTHLY",
                  "intervalCount": 1,
                  "startDate": "2026-08-05",
                  "reminderDaysBefore": 0,
                  "defaultWalletId": "%s",
                  "defaultCategoryId": "%s"
                }
                """.formatted(wallet.getId(), category.getId());
        mockMvc.perform(post("/api/workspaces/" + workspace.getId() + "/recurring-obligations")
                        .contentType("application/json")
                        .content(body)
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Rent"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.defaultWallet.name").value("Cash"));
        mockMvc.perform(get("/api/workspaces/" + workspace.getId() + "/recurring-obligations")
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].nextDueDate").value("2026-08-05"));
        mockMvc.perform(post("/api/workspaces/" + workspace.getId() + "/recurring-obligations")
                        .contentType("application/json")
                        .content(body.replace("\"direction\": \"PAYABLE\"", "\"direction\": \"PAYABLE\",\n  \"spendingScope\": \"NOPE\""))
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message", not(containsString("SpendingScope"))))
                .andExpect(jsonPath("$.message", not(containsString("constraint"))));

        assertThat(occurrenceRepository.count()).isZero();
    }

    private TestContext createContext(String prefix, WorkspaceRole role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.saveAndFlush(User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .fullName("Recurring Obligation Test User")
                .build());
        Workspace workspace = workspaceRepository.saveAndFlush(Workspace.builder()
                .name(prefix + " workspace")
                .createdByUser(user)
                .build());
        workspaceMemberRepository.saveAndFlush(WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(role)
                .build());
        return new TestContext(user, workspace);
    }

    private RecurringObligationTemplateRequest request(
            String name,
            ObligationDirection direction,
            ObligationAmountMode amountMode,
            String amount,
            ObligationFrequency frequency,
            Integer interval,
            String start,
            String end,
            Integer reminder,
            UUID walletId,
            UUID categoryId,
            String note) {
        RecurringObligationTemplateRequest request = new RecurringObligationTemplateRequest();
        request.setName(name);
        request.setDirection(direction);
        request.setAmountMode(amountMode);
        request.setDefaultAmount(amount == null ? null : new BigDecimal(amount));
        request.setFrequency(frequency);
        request.setIntervalCount(interval);
        request.setStartDate(LocalDate.parse(start));
        request.setEndDate(end == null ? null : LocalDate.parse(end));
        request.setReminderDaysBefore(reminder);
        request.setDefaultWalletId(walletId);
        request.setDefaultCategoryId(categoryId);
        request.setNote(note);
        return request;
    }

    private RecurringObligationPreviewRequest preview(
            ObligationFrequency frequency,
            Integer interval,
            String start,
            String end,
            Integer reminder,
            Integer count) {
        RecurringObligationPreviewRequest request = new RecurringObligationPreviewRequest();
        request.setFrequency(frequency);
        request.setIntervalCount(interval);
        request.setStartDate(LocalDate.parse(start));
        request.setEndDate(end == null ? null : LocalDate.parse(end));
        request.setReminderDaysBefore(reminder);
        request.setCount(count);
        return request;
    }

    private Wallet wallet(TestContext ctx, String name, boolean active) {
        return walletRepository.saveAndFlush(Wallet.builder()
                .workspace(ctx.workspace())
                .name(name)
                .walletType(WalletType.CASH)
                .openingBalance(BigDecimal.ZERO)
                .isActive(active)
                .build());
    }

    private Category category(TestContext ctx, String name, CategoryType type, boolean active, boolean archived) {
        return categoryRepository.saveAndFlush(Category.builder()
                .workspace(ctx.workspace())
                .name(name)
                .categoryType(type)
                .isActive(active)
                .isArchived(archived)
                .build());
    }

    private RegisterRequest registerRequest(String username) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setEmail(username + "@example.com");
        request.setPassword("StrongPassword123");
        request.setFullName("Recurring API User");
        return request;
    }

    private LoginRequest loginRequest(String username) {
        LoginRequest request = new LoginRequest();
        request.setIdentifier(username);
        request.setPassword("StrongPassword123");
        return request;
    }

    private void assertBusinessCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(code);
    }

    private record TestContext(User user, Workspace workspace) {
    }
}
