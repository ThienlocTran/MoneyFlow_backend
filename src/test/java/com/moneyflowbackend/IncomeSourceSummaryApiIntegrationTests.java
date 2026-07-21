package com.moneyflowbackend;

import com.moneyflowbackend.auth.dto.LoginRequest;
import com.moneyflowbackend.auth.dto.RegisterRequest;
import com.moneyflowbackend.auth.dto.TokenResponse;
import com.moneyflowbackend.auth.dto.UserResponse;
import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.auth.service.AuthService;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.income.dto.IncomeSourceSummaryListResponse;
import com.moneyflowbackend.income.dto.IncomeSourceSummaryResponse;
import com.moneyflowbackend.income.model.IncomeSource;
import com.moneyflowbackend.income.model.IncomeSourceStatus;
import com.moneyflowbackend.income.model.IncomeSourceType;
import com.moneyflowbackend.income.repository.IncomeSourceRepository;
import com.moneyflowbackend.income.service.IncomeSourceService;
import com.moneyflowbackend.transaction.model.Transaction;
import com.moneyflowbackend.transaction.model.TransactionStatus;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IncomeSourceSummaryApiIntegrationTests {
    @Autowired IncomeSourceService incomeSourceService;
    @Autowired IncomeSourceRepository incomeSourceRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired AuthService authService;
    @Autowired MockMvc mockMvc;
    @Autowired EntityManagerFactory entityManagerFactory;

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-07-21T01:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Test
    void detailSummaryCalculatesTotalsAndExcludesNonMatchingTransactions() {
        TestContext ctx = createContext("summary_calc", WorkspaceRole.OWNER);
        IncomeSource main = source(ctx, "Salary", IncomeSourceStatus.ACTIVE);
        IncomeSource other = source(ctx, "Other", IncomeSourceStatus.ACTIVE);

        tx(ctx, TransactionType.INCOME, TransactionStatus.POSTED, "100.25", "2026-07-01", main, null, false, "Gross 1");
        tx(ctx, TransactionType.INCOME, TransactionStatus.POSTED, "200.75", "2026-07-15", main, null, false, "Gross 2");
        tx(ctx, TransactionType.EXPENSE, TransactionStatus.POSTED, "80.10", "2026-07-20", null, main, false, "Direct expense");
        tx(ctx, TransactionType.INCOME, TransactionStatus.POSTED, "999.00", "2026-07-10", null, null, false, "Unlinked income");
        tx(ctx, TransactionType.EXPENSE, TransactionStatus.POSTED, "999.00", "2026-07-10", null, null, false, "Unlinked expense");
        tx(ctx, TransactionType.INCOME, TransactionStatus.PLANNED, "999.00", "2026-07-10", main, null, false, "Planned income");
        tx(ctx, TransactionType.EXPENSE, TransactionStatus.PLANNED, "999.00", "2026-07-10", null, main, false, "Planned expense");
        tx(ctx, TransactionType.INCOME, TransactionStatus.VOID, "999.00", "2026-07-10", main, null, false, "Void income");
        tx(ctx, TransactionType.INCOME, TransactionStatus.POSTED, "999.00", "2026-07-10", main, null, true, "Deleted income");
        tx(ctx, TransactionType.INCOME, TransactionStatus.POSTED, "999.00", "2026-07-10", other, null, false, "Other source income");
        tx(ctx, TransactionType.EXPENSE, TransactionStatus.POSTED, "999.00", "2026-07-10", null, other, false, "Other source expense");
        tx(ctx, TransactionType.TRANSFER, TransactionStatus.POSTED, "999.00", "2026-07-10", null, null, false, "Transfer");
        tx(ctx, TransactionType.ADJUSTMENT, TransactionStatus.POSTED, "999.00", "2026-07-10", null, null, false, "Adjustment");
        tx(ctx, TransactionType.LOAN_DISBURSEMENT, TransactionStatus.POSTED, "999.00", "2026-07-10", null, null, false, "Debt principal");

        IncomeSourceSummaryResponse summary = incomeSourceService.summary(
                ctx.workspace().getId(), main.getId(), "2026-07-01", "2026-08-01", ctx.user().getId());

        assertThat(summary.getGrossIncome()).isEqualByComparingTo("301.00");
        assertThat(summary.getDirectExpenses()).isEqualByComparingTo("80.10");
        assertThat(summary.getNetIncome()).isEqualByComparingTo("220.90");
        assertThat(summary.getIncomeTransactionCount()).isEqualTo(2);
        assertThat(summary.getExpenseTransactionCount()).isEqualTo(1);
        assertThat(summary.getCurrency()).isEqualTo("VND");
    }

    @Test
    void dateBoundariesDefaultCurrentMonthAndInvalidRangesWork() {
        TestContext ctx = createContext("summary_dates", WorkspaceRole.OWNER);
        IncomeSource source = source(ctx, "Default Month", IncomeSourceStatus.ACTIVE);
        tx(ctx, TransactionType.INCOME, TransactionStatus.POSTED, "10.00", "2026-06-30", source, null, false, "Before");
        tx(ctx, TransactionType.INCOME, TransactionStatus.POSTED, "20.00", "2026-07-01", source, null, false, "From inclusive");
        tx(ctx, TransactionType.EXPENSE, TransactionStatus.POSTED, "5.00", "2026-07-31", null, source, false, "July expense");
        tx(ctx, TransactionType.INCOME, TransactionStatus.POSTED, "30.00", "2026-08-01", source, null, false, "To exclusive");
        tx(ctx, TransactionType.INCOME, TransactionStatus.POSTED, "40.00", "2024-02-29", source, null, false, "Leap day");

        IncomeSourceSummaryResponse defaultMonth = incomeSourceService.summary(
                ctx.workspace().getId(), source.getId(), null, null, ctx.user().getId());
        assertThat(defaultMonth.getFrom()).isEqualTo(LocalDate.parse("2026-07-01"));
        assertThat(defaultMonth.getToExclusive()).isEqualTo(LocalDate.parse("2026-08-01"));
        assertThat(defaultMonth.getGrossIncome()).isEqualByComparingTo("20.00");
        assertThat(defaultMonth.getDirectExpenses()).isEqualByComparingTo("5.00");

        IncomeSourceSummaryResponse leapDay = incomeSourceService.summary(
                ctx.workspace().getId(), source.getId(), "2024-02-29", "2024-03-01", ctx.user().getId());
        assertThat(leapDay.getGrossIncome()).isEqualByComparingTo("40.00");

        assertBusinessCode(() -> incomeSourceService.summary(ctx.workspace().getId(), source.getId(), "2026-07-01", null, ctx.user().getId()), "VALIDATION_ERROR");
        assertBusinessCode(() -> incomeSourceService.summary(ctx.workspace().getId(), source.getId(), null, "2026-08-01", ctx.user().getId()), "VALIDATION_ERROR");
        assertBusinessCode(() -> incomeSourceService.summary(ctx.workspace().getId(), source.getId(), "2026-07-01", "2026-07-01", ctx.user().getId()), "VALIDATION_ERROR");
        assertBusinessCode(() -> incomeSourceService.summary(ctx.workspace().getId(), source.getId(), "2026-08-01", "2026-07-01", ctx.user().getId()), "VALIDATION_ERROR");
    }

    @Test
    void collectionSummaryFiltersSearchOrderingArchivedAndWorkspaceIsolationWork() {
        TestContext ctx = createContext("summary_collection", WorkspaceRole.OWNER);
        TestContext other = createContext("summary_collection_other", WorkspaceRole.OWNER);
        IncomeSource alpha = source(ctx, "Alpha Job", IncomeSourceStatus.ACTIVE);
        IncomeSource beta = source(ctx, "beta Shop", IncomeSourceStatus.ACTIVE);
        IncomeSource archived = source(ctx, "Alpha Job", IncomeSourceStatus.ARCHIVED);
        IncomeSource empty = source(ctx, "z Empty", IncomeSourceStatus.ACTIVE);
        IncomeSource otherSource = source(other, "Alpha Job", IncomeSourceStatus.ACTIVE);
        tx(ctx, TransactionType.INCOME, TransactionStatus.POSTED, "100.00", "2026-07-05", alpha, null, false, "Alpha income");
        tx(ctx, TransactionType.EXPENSE, TransactionStatus.POSTED, "25.00", "2026-07-05", null, archived, false, "Archived expense");
        tx(other, TransactionType.INCOME, TransactionStatus.POSTED, "999.00", "2026-07-05", otherSource, null, false, "Other workspace");

        IncomeSourceSummaryListResponse active = incomeSourceService.summaries(
                ctx.workspace().getId(), null, null, "2026-07-01", "2026-08-01", ctx.user().getId());
        assertThat(active.getItems()).extracting("incomeSourceId")
                .containsExactly(alpha.getId(), beta.getId(), empty.getId());
        assertThat(active.getItems()).filteredOn(item -> item.getIncomeSourceId().equals(empty.getId()))
                .first()
                .extracting("grossIncome", "directExpenses", "netIncome")
                .containsExactly(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        IncomeSourceSummaryListResponse search = incomeSourceService.summaries(
                ctx.workspace().getId(), IncomeSourceStatus.ACTIVE, "  ALPHA  ", "2026-07-01", "2026-08-01", ctx.user().getId());
        assertThat(search.getItems()).extracting("incomeSourceId").containsExactly(alpha.getId());

        IncomeSourceSummaryListResponse archivedOnly = incomeSourceService.summaries(
                ctx.workspace().getId(), IncomeSourceStatus.ARCHIVED, null, "2026-07-01", "2026-08-01", ctx.user().getId());
        assertThat(archivedOnly.getItems()).extracting("incomeSourceId").containsExactly(archived.getId());
        assertThat(archivedOnly.getItems().getFirst().getDirectExpenses()).isEqualByComparingTo("25.00");
    }

    @Test
    void ownerEditorViewerCanReadAndNonMembersDoNotGetCrossWorkspaceLeak() {
        TestContext owner = createContext("summary_auth_owner", WorkspaceRole.OWNER);
        TestContext editor = createContext("summary_auth_editor", WorkspaceRole.OWNER);
        TestContext viewer = createContext("summary_auth_viewer", WorkspaceRole.OWNER);
        TestContext outsider = createContext("summary_auth_outsider", WorkspaceRole.OWNER);
        workspaceMemberRepository.saveAndFlush(member(owner.workspace(), editor.user(), WorkspaceRole.EDITOR));
        workspaceMemberRepository.saveAndFlush(member(owner.workspace(), viewer.user(), WorkspaceRole.VIEWER));
        IncomeSource source = source(owner, "Readable", IncomeSourceStatus.ACTIVE);

        assertThat(incomeSourceService.summary(owner.workspace().getId(), source.getId(), null, null, owner.user().getId()).getIncomeSourceId()).isEqualTo(source.getId());
        assertThat(incomeSourceService.summary(owner.workspace().getId(), source.getId(), null, null, editor.user().getId()).getIncomeSourceId()).isEqualTo(source.getId());
        assertThat(incomeSourceService.summary(owner.workspace().getId(), source.getId(), null, null, viewer.user().getId()).getIncomeSourceId()).isEqualTo(source.getId());
        assertBusinessCode(() -> incomeSourceService.summaries(owner.workspace().getId(), null, null, null, null, outsider.user().getId()), "WORKSPACE_ACCESS_DENIED");
        assertBusinessCode(() -> incomeSourceService.summary(outsider.workspace().getId(), source.getId(), null, null, outsider.user().getId()), "INCOME_SOURCE_NOT_FOUND");
    }

    @Test
    void httpSummaryResponseIsWrappedAndDoesNotExposePrivateTransactionFields() throws Exception {
        String username = "summary_http_" + UUID.randomUUID().toString().substring(0, 8);
        UserResponse registered = authService.register(registerRequest(username));
        TokenResponse token = authService.login(loginRequest(username));
        Workspace workspace = workspaceRepository.findAllByUserId(registered.getId()).getFirst();
        User user = userRepository.findById(registered.getId()).orElseThrow();
        IncomeSource source = incomeSourceRepository.saveAndFlush(IncomeSource.builder()
                .workspace(workspace)
                .name("HTTP Salary")
                .type(IncomeSourceType.SALARY)
                .status(IncomeSourceStatus.ACTIVE)
                .createdByUser(user)
                .build());
        transactionRepository.saveAndFlush(Transaction.builder()
                .workspace(workspace)
                .createdByUser(user)
                .transactionType(TransactionType.INCOME)
                .transactionStatus(TransactionStatus.POSTED)
                .amount(new BigDecimal("123.00"))
                .currency("VND")
                .transactionDate(LocalDate.parse("2026-07-10"))
                .incomeSource(source)
                .description("Public description")
                .note("private note")
                .rawInput("private raw")
                .build());

        mockMvc.perform(get("/api/workspaces/" + workspace.getId() + "/income-sources/" + source.getId() + "/summary")
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.incomeSourceId").value(source.getId().toString()))
                .andExpect(jsonPath("$.data.grossIncome").value(123.00))
                .andExpect(jsonPath("$.data.note").doesNotExist())
                .andExpect(jsonPath("$.data.rawInput").doesNotExist())
                .andExpect(jsonPath("$.data.transactions").doesNotExist())
                .andExpect(jsonPath("$.data.hibernateLazyInitializer").doesNotExist());

        mockMvc.perform(get("/api/workspaces/" + workspace.getId() + "/income-sources/" + source.getId() + "/summary?from=2026-07-01")
                        .header("Authorization", "Bearer " + token.getAccessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message", not(org.hamcrest.Matchers.containsString("SQL"))));
    }

    @Test
    void collectionSummaryUsesConstantQueryCountAndRejectsMixedCurrency() {
        TestContext ctx = createContext("summary_perf", WorkspaceRole.OWNER);
        List<IncomeSource> sources = List.of(
                source(ctx, "A", IncomeSourceStatus.ACTIVE),
                source(ctx, "B", IncomeSourceStatus.ACTIVE),
                source(ctx, "C", IncomeSourceStatus.ACTIVE));
        sources.forEach(source -> tx(ctx, TransactionType.INCOME, TransactionStatus.POSTED, "10.00", "2026-07-10", source, null, false, "Income"));

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        IncomeSourceSummaryListResponse response = incomeSourceService.summaries(
                ctx.workspace().getId(), IncomeSourceStatus.ACTIVE, null, "2026-07-01", "2026-08-01", ctx.user().getId());
        assertThat(response.getItems()).hasSize(3);
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(6);

        Transaction usd = tx(ctx, TransactionType.INCOME, TransactionStatus.POSTED, "10.00", "2026-07-10", sources.getFirst(), null, false, "USD income");
        usd.setCurrency("USD");
        transactionRepository.saveAndFlush(usd);
        assertBusinessCode(() -> incomeSourceService.summaries(
                ctx.workspace().getId(), IncomeSourceStatus.ACTIVE, null, "2026-07-01", "2026-08-01", ctx.user().getId()), "INVALID_CURRENCY_AGGREGATION");
    }

    private Transaction tx(TestContext ctx, TransactionType type, TransactionStatus status, String amount, String date,
                           IncomeSource incomeSource, IncomeSource relatedIncomeSource, boolean deleted, String description) {
        return transactionRepository.saveAndFlush(Transaction.builder()
                .workspace(ctx.workspace())
                .createdByUser(ctx.user())
                .transactionType(type)
                .transactionStatus(status)
                .amount(new BigDecimal(amount))
                .currency("VND")
                .transactionDate(LocalDate.parse(date))
                .incomeSource(incomeSource)
                .relatedIncomeSource(relatedIncomeSource)
                .description(description)
                .deletedAt(deleted ? Instant.parse("2026-07-22T00:00:00Z") : null)
                .build());
    }

    private IncomeSource source(TestContext ctx, String name, IncomeSourceStatus status) {
        return incomeSourceRepository.saveAndFlush(IncomeSource.builder()
                .workspace(ctx.workspace())
                .name(name)
                .type(IncomeSourceType.OTHER)
                .status(status)
                .createdByUser(ctx.user())
                .build());
    }

    private TestContext createContext(String prefix, WorkspaceRole role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.saveAndFlush(User.builder()
                .username(prefix + "_" + suffix)
                .email(prefix + "_" + suffix + "@example.com")
                .fullName("Income Summary Test User")
                .build());
        Workspace workspace = workspaceRepository.saveAndFlush(Workspace.builder()
                .name(prefix + " workspace")
                .createdByUser(user)
                .currency("VND")
                .timezone("Asia/Ho_Chi_Minh")
                .build());
        workspaceMemberRepository.saveAndFlush(member(workspace, user, role));
        return new TestContext(user, workspace);
    }

    private WorkspaceMember member(Workspace workspace, User user, WorkspaceRole role) {
        return WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(role)
                .build();
    }

    private RegisterRequest registerRequest(String username) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setEmail(username + "@example.com");
        request.setPassword("StrongPassword123");
        request.setFullName("Income Summary API User");
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
