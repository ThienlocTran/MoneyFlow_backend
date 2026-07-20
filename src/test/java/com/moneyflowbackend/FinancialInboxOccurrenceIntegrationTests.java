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
import com.moneyflowbackend.obligation.dto.FinancialInboxGroup;
import com.moneyflowbackend.obligation.dto.FinancialInboxResponse;
import com.moneyflowbackend.obligation.dto.ObligationOccurrencePageResponse;
import com.moneyflowbackend.obligation.dto.ObligationOccurrenceResponse;
import com.moneyflowbackend.obligation.dto.SkipOccurrenceRequest;
import com.moneyflowbackend.obligation.dto.SnoozeOccurrenceRequest;
import com.moneyflowbackend.obligation.model.ObligationAmountMode;
import com.moneyflowbackend.obligation.model.ObligationDirection;
import com.moneyflowbackend.obligation.model.ObligationFrequency;
import com.moneyflowbackend.obligation.model.ObligationOccurrence;
import com.moneyflowbackend.obligation.model.ObligationOccurrenceStatus;
import com.moneyflowbackend.obligation.model.RecurringObligationStatus;
import com.moneyflowbackend.obligation.model.RecurringObligationTemplate;
import com.moneyflowbackend.obligation.repository.ObligationOccurrenceRepository;
import com.moneyflowbackend.obligation.repository.RecurringObligationTemplateRepository;
import com.moneyflowbackend.obligation.service.FinancialInboxService;
import com.moneyflowbackend.obligation.service.ObligationOccurrenceService;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FinancialInboxOccurrenceIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired FinancialInboxService inboxService;
    @Autowired ObligationOccurrenceService occurrenceService;
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
    void inboxLazilyGeneratesActiveTemplatesAndProjectsGroupsWithoutTransactions() {
        TestContext ctx = createContext("b4_inbox", WorkspaceRole.OWNER);
        Wallet wallet = wallet(ctx);
        Category expense = category(ctx, CategoryType.EXPENSE);
        RecurringObligationTemplate active = templateRepository.saveAndFlush(template(ctx, "Rent", RecurringObligationStatus.ACTIVE, wallet, expense)
                .frequency(ObligationFrequency.WEEKLY)
                .startDate(LocalDate.of(2026, 7, 13))
                .endDate(LocalDate.of(2026, 7, 27))
                .build());
        RecurringObligationTemplate paused = templateRepository.saveAndFlush(template(ctx, "Paused", RecurringObligationStatus.PAUSED, wallet, expense)
                .startDate(LocalDate.of(2026, 7, 20))
                .build());
        RecurringObligationTemplate archived = templateRepository.saveAndFlush(template(ctx, "Archived", RecurringObligationStatus.ARCHIVED, wallet, expense)
                .startDate(LocalDate.of(2026, 7, 20))
                .build());
        ObligationOccurrence oldOverdue = occurrenceRepository.saveAndFlush(occurrence(ctx, active, "2020-01-20", LocalDate.of(2020, 1, 20)));
        ObligationOccurrence snoozed = occurrenceRepository.saveAndFlush(occurrence(ctx, active, "2026-07-10", LocalDate.of(2026, 7, 10)));
        snoozed.setSnoozedUntil(LocalDate.of(2026, 7, 25));
        ObligationOccurrence skipped = occurrence(ctx, active, "2026-07-11", LocalDate.of(2026, 7, 11));
        skipped.setStatus(ObligationOccurrenceStatus.SKIPPED);
        skipped.setSkippedAt(Instant.parse("2026-07-11T00:00:00Z"));
        occurrenceRepository.saveAndFlush(skipped);
        long transactionsBefore = transactionRepository.count();

        FinancialInboxResponse response = inboxService.inbox(
                ctx.workspace().getId(),
                null,
                null,
                null,
                null,
                null,
                0,
                20,
                ctx.user().getId());

        assertThat(response.getSummary().getOverdueCount()).isEqualTo(2);
        assertThat(response.getSummary().getDueTodayCount()).isEqualTo(1);
        assertThat(response.getSummary().getUpcomingCount()).isEqualTo(1);
        assertThat(response.getSummary().getSnoozedCount()).isEqualTo(1);
        assertThat(response.getSummary().getTotalPendingCount()).isEqualTo(5);
        assertThat(response.getContent()).extracting(ObligationOccurrenceResponse::getInboxGroup)
                .containsExactly(
                        FinancialInboxGroup.OVERDUE,
                        FinancialInboxGroup.OVERDUE,
                        FinancialInboxGroup.DUE_TODAY,
                        FinancialInboxGroup.UPCOMING,
                        FinancialInboxGroup.SNOOZED);
        assertThat(response.getContent()).extracting(ObligationOccurrenceResponse::getId).contains(oldOverdue.getId());
        assertThat(response.getContent()).noneMatch(item -> item.getStatus() != ObligationOccurrenceStatus.PENDING);
        assertThat(occurrences(paused)).isZero();
        assertThat(occurrences(archived)).isZero();
        assertThat(transactionRepository.count()).isEqualTo(transactionsBefore);

        FinancialInboxResponse rerun = inboxService.inbox(ctx.workspace().getId(), null, null, null, null, null, 0, 20, ctx.user().getId());
        assertThat(rerun.getSummary().getTotalPendingCount()).isEqualTo(5);
    }

    @Test
    void inboxFiltersDateGroupDirectionAndSummaryBeforeGroupFilter() {
        TestContext ctx = createContext("b4_filters", WorkspaceRole.OWNER);
        Category expense = category(ctx, CategoryType.EXPENSE);
        RecurringObligationTemplate payable = templateRepository.saveAndFlush(template(ctx, "Payable", RecurringObligationStatus.ACTIVE, null, expense)
                .direction(ObligationDirection.PAYABLE)
                .status(RecurringObligationStatus.PAUSED)
                .startDate(LocalDate.of(2026, 7, 20))
                .build());
        occurrenceRepository.saveAndFlush(occurrence(ctx, payable, "2026-07-20", LocalDate.of(2026, 7, 20)));
        occurrenceRepository.saveAndFlush(occurrence(ctx, payable, "2026-08-25", LocalDate.of(2026, 8, 25)));

        FinancialInboxResponse response = inboxService.inbox(
                ctx.workspace().getId(),
                FinancialInboxGroup.UPCOMING,
                ObligationDirection.PAYABLE,
                payable.getId(),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                0,
                1,
                ctx.user().getId());

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().getFirst().getDueDate()).isEqualTo(LocalDate.of(2026, 8, 25));
        assertThat(response.getSummary().getDueTodayCount()).isZero();
        assertThat(response.getSummary().getUpcomingCount()).isEqualTo(1);
        assertThat(response.getTotalElements()).isEqualTo(1);

        assertBusinessCode(() -> inboxService.inbox(ctx.workspace().getId(), null, null, null,
                LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 1), 0, 20, ctx.user().getId()), "INVALID_DATE_RANGE");
    }

    @Test
    void historyReadsMaterializedOccurrencesOnlyAndDoesNotGenerate() {
        TestContext ctx = createContext("b4_history", WorkspaceRole.OWNER);
        RecurringObligationTemplate template = templateRepository.saveAndFlush(template(ctx, "History", RecurringObligationStatus.ACTIVE, null, category(ctx, CategoryType.EXPENSE))
                .startDate(LocalDate.of(2026, 6, 20))
                .build());
        ObligationOccurrence pending = occurrenceRepository.saveAndFlush(occurrence(ctx, template, "2026-06-20", LocalDate.of(2026, 6, 20)));
        ObligationOccurrence skipped = occurrence(ctx, template, "2026-05-20", LocalDate.of(2026, 5, 20));
        skipped.setStatus(ObligationOccurrenceStatus.SKIPPED);
        skipped.setSkippedAt(Instant.parse("2026-05-20T00:00:00Z"));
        skipped = occurrenceRepository.saveAndFlush(skipped);
        long before = occurrenceRepository.count();

        ObligationOccurrencePageResponse response = inboxService.history(
                ctx.workspace().getId(),
                template.getId(),
                null,
                null,
                null,
                0,
                20,
                ctx.user().getId());

        assertThat(response.getContent()).extracting(ObligationOccurrenceResponse::getId)
                .containsExactly(pending.getId(), skipped.getId());
        assertThat(occurrenceRepository.count()).isEqualTo(before);
    }

    @Test
    void skipSnoozeAndReopenRespectStateAuthorizationAndDoNotCreateTransactions() {
        TestContext owner = createContext("b4_actions", WorkspaceRole.OWNER);
        TestContext viewer = createContext("b4_actions_viewer", WorkspaceRole.OWNER);
        workspaceMemberRepository.saveAndFlush(WorkspaceMember.builder().workspace(owner.workspace()).user(viewer.user()).role(WorkspaceRole.VIEWER).build());
        RecurringObligationTemplate template = templateRepository.saveAndFlush(template(owner, "Action", RecurringObligationStatus.ACTIVE, null, category(owner, CategoryType.EXPENSE)).build());
        ObligationOccurrence occurrence = occurrenceRepository.saveAndFlush(occurrence(owner, template, "2026-07-19", LocalDate.of(2026, 7, 19)));
        long transactionsBefore = transactionRepository.count();

        SnoozeOccurrenceRequest snooze = new SnoozeOccurrenceRequest();
        snooze.setSnoozedUntil(LocalDate.of(2026, 7, 25));
        assertThat(occurrenceService.snooze(owner.workspace().getId(), occurrence.getId(), snooze, owner.user().getId()).getSnoozedUntil())
                .isEqualTo(LocalDate.of(2026, 7, 25));
        SnoozeOccurrenceRequest badSnooze = new SnoozeOccurrenceRequest();
        badSnooze.setSnoozedUntil(LocalDate.of(2026, 7, 20));
        assertBusinessCode(() -> occurrenceService.snooze(owner.workspace().getId(), occurrence.getId(), badSnooze, owner.user().getId()), "INVALID_SNOOZE_DATE");

        SkipOccurrenceRequest skip = new SkipOccurrenceRequest();
        skip.setReason("  paid outside app  ");
        ObligationOccurrenceResponse skipped = occurrenceService.skip(owner.workspace().getId(), occurrence.getId(), skip, owner.user().getId());
        assertThat(skipped.getStatus()).isEqualTo(ObligationOccurrenceStatus.SKIPPED);
        assertThat(skipped.getSkipReason()).isEqualTo("paid outside app");
        assertThat(skipped.getSkippedAt()).isNotNull();
        assertThat(skipped.getSnoozedUntil()).isNull();
        assertThat(occurrenceService.skip(owner.workspace().getId(), occurrence.getId(), skip, owner.user().getId()).getSkippedAt())
                .isEqualTo(skipped.getSkippedAt());
        assertBusinessCode(() -> occurrenceService.snooze(owner.workspace().getId(), occurrence.getId(), snooze, owner.user().getId()), "INVALID_OCCURRENCE_STATE");

        ObligationOccurrenceResponse reopened = occurrenceService.reopen(owner.workspace().getId(), occurrence.getId(), owner.user().getId());
        assertThat(reopened.getStatus()).isEqualTo(ObligationOccurrenceStatus.PENDING);
        assertThat(reopened.getSkippedAt()).isNull();
        assertThat(reopened.getSkipReason()).isNull();
        assertThat(occurrenceService.reopen(owner.workspace().getId(), occurrence.getId(), owner.user().getId()).getStatus())
                .isEqualTo(ObligationOccurrenceStatus.PENDING);
        assertBusinessCode(() -> occurrenceService.skip(owner.workspace().getId(), occurrence.getId(), skip, viewer.user().getId()), "FORBIDDEN");
        assertThat(transactionRepository.count()).isEqualTo(transactionsBefore);
    }

    @Test
    void controllerPathsReturnWrappedJson() throws Exception {
        String username = "b4_http_" + UUID.randomUUID().toString().substring(0, 8);
        UserResponse registered = authService.register(registerRequest(username));
        TokenResponse token = authService.login(loginRequest(username));
        Workspace workspace = workspaceRepository.findAllByUserId(registered.getId()).getFirst();
        User user = userRepository.findById(registered.getId()).orElseThrow();
        Category category = categoryRepository.saveAndFlush(Category.builder()
                .workspace(workspace)
                .name("Rent")
                .categoryType(CategoryType.EXPENSE)
                .build());
        RecurringObligationTemplate template = templateRepository.saveAndFlush(RecurringObligationTemplate.builder()
                .workspace(workspace)
                .name("Rent")
                .direction(ObligationDirection.PAYABLE)
                .amountMode(ObligationAmountMode.FIXED)
                .defaultAmount(new BigDecimal("100"))
                .frequency(ObligationFrequency.MONTHLY)
                .intervalCount(1)
                .startDate(LocalDate.of(2026, 7, 20))
                .reminderDaysBefore(0)
                .defaultCategory(category)
                .status(RecurringObligationStatus.ACTIVE)
                .createdByUser(user)
                .build());
        ObligationOccurrence occurrence = occurrenceRepository.saveAndFlush(occurrence(new TestContext(user, workspace), template, "2026-07-20", LocalDate.of(2026, 7, 20)));

        mockMvc.perform(get("/api/workspaces/" + workspace.getId() + "/financial-inbox")
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.dueTodayCount").value(1));
        mockMvc.perform(get("/api/workspaces/" + workspace.getId() + "/recurring-obligations/" + template.getId() + "/occurrences")
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
        mockMvc.perform(post("/api/workspaces/" + workspace.getId() + "/obligation-occurrences/" + occurrence.getId() + "/skip")
                        .contentType("application/json")
                        .content("{\"reason\":\"outside\"}")
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SKIPPED"));
    }

    private TestContext createContext(String prefix, WorkspaceRole role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.saveAndFlush(User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .fullName("B4 Test User")
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

    private RecurringObligationTemplate.RecurringObligationTemplateBuilder template(
            TestContext ctx,
            String name,
            RecurringObligationStatus status,
            Wallet wallet,
            Category category) {
        return RecurringObligationTemplate.builder()
                .workspace(ctx.workspace())
                .name(name)
                .direction(ObligationDirection.PAYABLE)
                .amountMode(ObligationAmountMode.FIXED)
                .defaultAmount(new BigDecimal("100"))
                .frequency(ObligationFrequency.MONTHLY)
                .intervalCount(1)
                .startDate(LocalDate.of(2026, 7, 20))
                .reminderDaysBefore(0)
                .defaultWallet(wallet)
                .defaultCategory(category)
                .status(status)
                .createdByUser(ctx.user());
    }

    private ObligationOccurrence occurrence(TestContext ctx, RecurringObligationTemplate template, String periodKey, LocalDate dueDate) {
        return ObligationOccurrence.builder()
                .workspace(ctx.workspace())
                .template(template)
                .periodKey(periodKey)
                .dueDate(dueDate)
                .expectedAmount(new BigDecimal("100"))
                .status(ObligationOccurrenceStatus.PENDING)
                .build();
    }

    private Wallet wallet(TestContext ctx) {
        return walletRepository.saveAndFlush(Wallet.builder()
                .workspace(ctx.workspace())
                .name("Cash")
                .walletType(WalletType.CASH)
                .openingBalance(BigDecimal.ZERO)
                .build());
    }

    private Category category(TestContext ctx, CategoryType type) {
        return categoryRepository.saveAndFlush(Category.builder()
                .workspace(ctx.workspace())
                .name(type.name() + UUID.randomUUID().toString().substring(0, 8))
                .categoryType(type)
                .build());
    }

    private long occurrences(RecurringObligationTemplate template) {
        return occurrenceRepository.findAll().stream()
                .filter(occurrence -> occurrence.getTemplate().getId().equals(template.getId()))
                .count();
    }

    private RegisterRequest registerRequest(String username) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setEmail(username + "@example.com");
        request.setPassword("StrongPassword123");
        request.setFullName("B4 API User");
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
