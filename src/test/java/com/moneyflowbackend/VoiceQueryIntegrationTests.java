package com.moneyflowbackend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneyflowbackend.auth.dto.LoginRequest;
import com.moneyflowbackend.auth.dto.RegisterRequest;
import com.moneyflowbackend.auth.dto.TokenResponse;
import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.auth.service.AuthService;
import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.transaction.model.Transaction;
import com.moneyflowbackend.transaction.model.TransactionSourceType;
import com.moneyflowbackend.transaction.model.TransactionStatus;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.model.WalletType;
import com.moneyflowbackend.wallet.repository.WalletRepository;
import com.moneyflowbackend.workspace.model.PersonKind;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspacePerson;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspacePersonRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class VoiceQueryIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired WorkspacePersonRepository workspacePersonRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired TransactionRepository transactionRepository;
    @PersistenceContext EntityManager entityManager;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void unauthorizedRequestIsRejected() throws Exception {
        TestUser owner = registerAndLogin("voice_query_unauth");

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-query/ask", owner.workspace().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("text", "Hôm nay tôi tiêu bao nhiêu?"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void memberCanAskTodayAndMonthExpenseTotalsWithoutMutation() throws Exception {
        TestUser owner = registerAndLogin("voice_query_expense");
        Category food = category(owner.workspace(), "Ăn uống", CategoryType.EXPENSE);
        Wallet cash = wallet(owner.workspace(), "Cash");
        transaction(owner.workspace(), owner.user(), cash, food, TransactionType.EXPENSE, TransactionStatus.POSTED, "100000", LocalDate.of(2026, 8, 1), null, false, true);
        transaction(owner.workspace(), owner.user(), cash, food, TransactionType.EXPENSE, TransactionStatus.POSTED, "50000", LocalDate.of(2026, 8, 1), null, false, true);
        transaction(owner.workspace(), owner.user(), cash, food, TransactionType.EXPENSE, TransactionStatus.POSTED, "70000", LocalDate.of(2026, 7, 31), null, false, true);
        transaction(owner.workspace(), owner.user(), cash, food, TransactionType.EXPENSE, TransactionStatus.DRAFT, "90000", LocalDate.of(2026, 8, 1), null, false, true);
        transaction(owner.workspace(), owner.user(), cash, food, TransactionType.EXPENSE, TransactionStatus.POSTED, "80000", LocalDate.of(2026, 8, 1), Instant.now(), false, true);
        transaction(owner.workspace(), owner.user(), cash, food, TransactionType.EXPENSE, TransactionStatus.POSTED, "60000", LocalDate.of(2026, 8, 1), null, true, false);
        long txBefore = transactionRepository.count();
        long walletBefore = walletRepository.count();
        long debtBefore = debtCount();

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-query/ask", owner.workspace().getId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "text", "Hôm nay tôi tiêu bao nhiêu?",
                                "timezone", "Asia/Ho_Chi_Minh",
                                "now", "2026-08-01T07:35:00+07:00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("READ_ONLY_QUERY"))
                .andExpect(jsonPath("$.data.intent").value("TODAY_EXPENSE_TOTAL"))
                .andExpect(jsonPath("$.data.status").value("ANSWERED"))
                .andExpect(jsonPath("$.data.period.from").value("2026-08-01"))
                .andExpect(jsonPath("$.data.period.to").value("2026-08-01"))
                .andExpect(jsonPath("$.data.metrics[0].amount").value(150000))
                .andExpect(jsonPath("$.data.metrics[0].count").value(2));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-query/ask", owner.workspace().getId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("text", "tong chi thang nay?", "now", "2026-08-01T07:35:00+07:00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent").value("MONTH_EXPENSE_TOTAL"))
                .andExpect(jsonPath("$.data.metrics[0].amount").value(150000));

        org.assertj.core.api.Assertions.assertThat(transactionRepository.count()).isEqualTo(txBefore);
        org.assertj.core.api.Assertions.assertThat(walletRepository.count()).isEqualTo(walletBefore);
        org.assertj.core.api.Assertions.assertThat(debtCount()).isEqualTo(debtBefore);
    }

    @Test
    void monthIncomeTotalExcludesOtherWorkspace() throws Exception {
        TestUser owner = registerAndLogin("voice_query_income");
        TestUser other = registerAndLogin("voice_query_income_other");
        Category salary = category(owner.workspace(), "Lương", CategoryType.INCOME);
        Category otherSalary = category(other.workspace(), "Lương", CategoryType.INCOME);
        Wallet cash = wallet(owner.workspace(), "Cash");
        Wallet otherCash = wallet(other.workspace(), "Cash");
        transaction(owner.workspace(), owner.user(), cash, salary, TransactionType.INCOME, TransactionStatus.POSTED, "3000000", LocalDate.of(2026, 8, 1), null, false, true);
        transaction(other.workspace(), other.user(), otherCash, otherSalary, TransactionType.INCOME, TransactionStatus.POSTED, "9000000", LocalDate.of(2026, 8, 1), null, false, true);

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-query/ask", owner.workspace().getId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("text", "thang nay toi kiem duoc bao nhieu?", "now", "2026-08-01T07:35:00+07:00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent").value("MONTH_INCOME_TOTAL"))
                .andExpect(jsonPath("$.data.metrics[0].amount").value(3000000));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-query/ask", owner.workspace().getId())
                        .header("Authorization", bearer(other.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("text", "thang nay toi kiem duoc bao nhieu?", "now", "2026-08-01T07:35:00+07:00"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void topCategoryAndLargestExpenseReturnBreakdowns() throws Exception {
        TestUser owner = registerAndLogin("voice_query_breakdowns");
        Wallet cash = wallet(owner.workspace(), "Cash");
        Category food = category(owner.workspace(), "Food", CategoryType.EXPENSE);
        Category fuel = category(owner.workspace(), "Fuel", CategoryType.EXPENSE);
        transaction(owner.workspace(), owner.user(), cash, food, TransactionType.EXPENSE, TransactionStatus.POSTED, "200000", LocalDate.of(2026, 8, 1), null, false, true);
        transaction(owner.workspace(), owner.user(), cash, food, TransactionType.EXPENSE, TransactionStatus.POSTED, "150000", LocalDate.of(2026, 8, 2), null, false, true);
        transaction(owner.workspace(), owner.user(), cash, fuel, TransactionType.EXPENSE, TransactionStatus.POSTED, "500000", LocalDate.of(2026, 8, 3), null, false, true);

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-query/ask", owner.workspace().getId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("text", "thang nay tien di dau nhieu nhat?", "now", "2026-08-04T07:35:00+07:00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent").value("TOP_EXPENSE_CATEGORIES_THIS_MONTH"))
                .andExpect(jsonPath("$.data.breakdowns[0].items[0].label").value("Fuel"))
                .andExpect(jsonPath("$.data.breakdowns[0].items[0].amount").value(500000));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-query/ask", owner.workspace().getId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("text", "khoan chi lon nhat thang nay la gi?", "now", "2026-08-04T07:35:00+07:00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent").value("LARGEST_EXPENSE_THIS_MONTH"))
                .andExpect(jsonPath("$.data.breakdowns[0].items[0].label").value("Fuel"))
                .andExpect(jsonPath("$.data.breakdowns[0].items[0].amount").value(500000));
    }

    @Test
    void debtReceivableAndPayableSummariesAreReadOnly() throws Exception {
        TestUser owner = registerAndLogin("voice_query_debt");
        debt(owner.workspace(), "Bao", "RECEIVABLE", "1000000", "200000");
        debt(owner.workspace(), "Lan", "PAYABLE", "700000", "100000");
        long before = debtCount();

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-query/ask", owner.workspace().getId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("text", "ai con no toi bao nhieu?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent").value("RECEIVABLE_SUMMARY"))
                .andExpect(jsonPath("$.data.metrics[0].amount").value(800000))
                .andExpect(jsonPath("$.data.breakdowns[0].items[0].label").value("Bao"));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-query/ask", owner.workspace().getId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("text", "toi dang no ai?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent").value("PAYABLE_SUMMARY"))
                .andExpect(jsonPath("$.data.metrics[0].amount").value(600000))
                .andExpect(jsonPath("$.data.breakdowns[0].items[0].label").value("Lan"));

        org.assertj.core.api.Assertions.assertThat(debtCount()).isEqualTo(before);
    }

    @Test
    void ambiguousAndUnsupportedLanguageReturnFriendlyStatuses() throws Exception {
        TestUser owner = registerAndLogin("voice_query_friendly");

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-query/ask", owner.workspace().getId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("text", "tien sao roi?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NEEDS_CLARIFICATION"));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-query/ask", owner.workspace().getId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("text", "thang sau toi co du tien khong?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNSUPPORTED"));
    }

    private TestUser registerAndLogin(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        RegisterRequest request = new RegisterRequest();
        request.setUsername(prefix + "_" + suffix);
        request.setEmail(prefix + "_" + suffix + "@example.com");
        request.setPassword("StrongPassword123");
        request.setFullName("Voice Query Test User");
        authService.register(request);
        LoginRequest login = new LoginRequest();
        login.setIdentifier(request.getUsername());
        login.setPassword("StrongPassword123");
        TokenResponse token = authService.login(login);
        Workspace workspace = workspaceRepository.findAllByUserId(token.getUser().getId()).getFirst();
        User user = userRepository.getReferenceById(token.getUser().getId());
        return new TestUser(user, workspace, token);
    }

    private Wallet wallet(Workspace workspace, String name) {
        return walletRepository.saveAndFlush(Wallet.builder()
                .workspace(workspace)
                .name(name + " " + UUID.randomUUID())
                .walletType(WalletType.CASH)
                .openingBalance(BigDecimal.ZERO)
                .isActive(true)
                .includeInTotal(true)
                .build());
    }

    private Category category(Workspace workspace, String name, CategoryType type) {
        return categoryRepository.saveAndFlush(Category.builder()
                .workspace(workspace)
                .name(name)
                .categoryType(type)
                .isActive(true)
                .isArchived(false)
                .build());
    }

    private Transaction transaction(Workspace workspace, User user, Wallet wallet, Category category, TransactionType type, TransactionStatus status,
            String amount, LocalDate date, Instant deletedAt, boolean historical, boolean affectsWalletBalance) {
        return transactionRepository.saveAndFlush(Transaction.builder()
                .workspace(workspace)
                .createdByUser(user)
                .wallet(wallet)
                .category(category)
                .transactionType(type)
                .transactionStatus(status)
                .amount(new BigDecimal(amount))
                .currency("VND")
                .transactionDate(date)
                .description(category.getName())
                .sourceType(TransactionSourceType.MANUAL)
                .deletedAt(deletedAt)
                .historical(historical)
                .affectsWalletBalance(affectsWalletBalance)
                .build());
    }

    private void debt(Workspace workspace, String personName, String direction, String principal, String paid) {
        WorkspacePerson person = workspacePersonRepository.saveAndFlush(WorkspacePerson.builder()
                .workspace(workspace)
                .displayName(personName)
                .personKind(PersonKind.COUNTERPARTY)
                .isActive(true)
                .build());
        UUID debtId = UUID.randomUUID();
        entityManager.createNativeQuery("""
                INSERT INTO debts (id, workspace_id, counterparty_person_id, direction, principal_amount, opened_on, due_on, closed_on, debt_status, note, created_at, updated_at)
                VALUES (:id, :workspaceId, :personId, :direction, :amount, :openedOn, null, null, 'OPEN', null, NOW(), NOW())
                """)
                .setParameter("id", debtId)
                .setParameter("workspaceId", workspace.getId())
                .setParameter("personId", person.getId())
                .setParameter("direction", direction)
                .setParameter("amount", new BigDecimal(principal))
                .setParameter("openedOn", LocalDate.of(2026, 8, 1))
                .executeUpdate();
        entityManager.createNativeQuery("""
                INSERT INTO debt_payments (id, debt_id, amount, payment_date, note, created_at)
                VALUES (:id, :debtId, :amount, :paymentDate, null, NOW())
                """)
                .setParameter("id", UUID.randomUUID())
                .setParameter("debtId", debtId)
                .setParameter("amount", new BigDecimal(paid))
                .setParameter("paymentDate", LocalDate.of(2026, 8, 2))
                .executeUpdate();
    }

    private long debtCount() {
        return ((Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM debts").getSingleResult()).longValue();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String bearer(TokenResponse token) {
        return "Bearer " + token.getAccessToken();
    }

    private record TestUser(User user, Workspace workspace, TokenResponse token) {
    }
}
