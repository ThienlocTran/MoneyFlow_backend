package com.moneyflowbackend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneyflowbackend.auth.dto.LoginRequest;
import com.moneyflowbackend.auth.dto.RegisterRequest;
import com.moneyflowbackend.auth.dto.TokenResponse;
import com.moneyflowbackend.auth.dto.UserResponse;
import com.moneyflowbackend.auth.service.AuthService;
import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.jar.model.Jar;
import com.moneyflowbackend.jar.repository.JarRepository;
import com.moneyflowbackend.planning.dto.PlannedObligationRequest;
import com.moneyflowbackend.planning.model.PlannedObligation;
import com.moneyflowbackend.planning.model.PlannedObligationStatus;
import com.moneyflowbackend.planning.repository.PlannedObligationRepository;
import com.moneyflowbackend.planning.service.PlanningProjectionService;
import com.moneyflowbackend.transaction.dto.TransactionRequest;
import com.moneyflowbackend.transaction.dto.TransactionResponse;
import com.moneyflowbackend.transaction.model.Transaction;
import com.moneyflowbackend.transaction.model.TransactionStatus;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.transaction.service.TransactionService;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.model.WalletType;
import com.moneyflowbackend.wallet.repository.WalletRepository;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PlannedObligationPaymentIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired JarRepository jarRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired PlannedObligationRepository obligationRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired TransactionService transactionService;
    @Autowired PlanningProjectionService projectionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void linkExistingPostedExpenseMarksPaidWithoutCreatingTransaction() throws Exception {
        AuthContext auth = auth("planned_link");
        Wallet wallet = wallet(workspace(auth.workspaceId()), "Cash");
        Category category = category(workspace(auth.workspaceId()), "Rent");
        UUID obligationId = obligation(auth, "Rent", "2500000", LocalDate.now().plusDays(3));
        UUID transactionId = expense(auth, wallet, category, "2500000", TransactionStatus.POSTED, false);
        long txBefore = transactionRepository.count();

        mockMvc.perform(post(path(auth.workspaceId()) + "/" + obligationId + "/link-transaction")
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "transactionId", transactionId.toString(),
                                "paidAt", "2026-08-11T00:00:00Z",
                                "note", "Paid"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.linkedTransactionId").value(transactionId.toString()))
                .andExpect(jsonPath("$.data.paidAt").exists())
                .andExpect(jsonPath("$.data.paidNote").value("Paid"));

        assertThat(transactionRepository.count()).isEqualTo(txBefore);
        PlannedObligation saved = obligationRepository.findByIdAndWorkspaceIdAndDeletedAtIsNull(obligationId, auth.workspaceId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(PlannedObligationStatus.PAID);
    }

    @Test
    void linkTransactionValidationBlocksUnsafeTransactions() throws Exception {
        AuthContext auth = auth("planned_link_invalid");
        AuthContext other = auth("planned_link_other");
        Workspace workspace = workspace(auth.workspaceId());
        Wallet wallet = wallet(workspace, "Cash");
        Category category = category(workspace, "Rent");
        UUID obligationId = obligation(auth, "Rent", "1000", LocalDate.now().plusDays(3));

        UUID otherTx = expense(other, wallet(workspace(other.workspaceId()), "Other cash"), category(workspace(other.workspaceId()), "Other rent"), "1000", TransactionStatus.POSTED, false);
        mockMvc.perform(post(path(auth.workspaceId()) + "/" + obligationId + "/link-transaction")
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(linkJson(otherTx)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLANNING_OBLIGATION_TRANSACTION_NOT_FOUND"));

        UUID draft = expense(auth, wallet, category, "1000", TransactionStatus.PLANNED, false);
        mockMvc.perform(post(path(auth.workspaceId()) + "/" + obligationId + "/link-transaction")
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(linkJson(draft)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLANNING_OBLIGATION_TRANSACTION_NOT_POSTED"));

        UUID deleted = expense(auth, wallet, category, "1000", TransactionStatus.POSTED, true);
        mockMvc.perform(post(path(auth.workspaceId()) + "/" + obligationId + "/link-transaction")
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(linkJson(deleted)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLANNING_OBLIGATION_TRANSACTION_DELETED"));

        UUID income = income(auth, wallet, "1000");
        mockMvc.perform(post(path(auth.workspaceId()) + "/" + obligationId + "/link-transaction")
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(linkJson(income)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLANNING_OBLIGATION_TRANSACTION_TYPE_UNSUPPORTED"));

        UUID mismatch = expense(auth, wallet, category, "2000", TransactionStatus.POSTED, false);
        mockMvc.perform(post(path(auth.workspaceId()) + "/" + obligationId + "/link-transaction")
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(linkJson(mismatch)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLANNING_OBLIGATION_AMOUNT_MISMATCH"));

        assertThat(obligationRepository.findByIdAndWorkspaceIdAndDeletedAtIsNull(obligationId, auth.workspaceId()).orElseThrow().getStatus())
                .isEqualTo(PlannedObligationStatus.PLANNED);
    }

    @Test
    void markPaidCreatesOneExpenseAndRepeatIsIdempotent() throws Exception {
        AuthContext auth = auth("planned_mark_paid");
        Workspace workspace = workspace(auth.workspaceId());
        Wallet wallet = wallet(workspace, "Cash");
        Category category = category(workspace, "Rent");
        UUID obligationId = obligation(auth, "Rent", "2500000", LocalDate.now().plusDays(3));
        long txBefore = transactionRepository.count();

        String body = mockMvc.perform(post(path(auth.workspaceId()) + "/" + obligationId + "/mark-paid")
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "walletId", wallet.getId().toString(),
                                "categoryId", category.getId().toString(),
                                "amount", 2500000,
                                "transactionDate", LocalDate.now().toString(),
                                "note", "Paid now"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.obligation.status").value("PAID"))
                .andExpect(jsonPath("$.data.transactionId").exists())
                .andExpect(jsonPath("$.data.transaction.type").value("EXPENSE"))
                .andExpect(jsonPath("$.data.transaction.walletId").value(wallet.getId().toString()))
                .andExpect(jsonPath("$.data.transaction.categoryId").value(category.getId().toString()))
                .andReturn().getResponse().getContentAsString();
        String transactionId = objectMapper.readTree(body).path("data").path("transactionId").asText();

        mockMvc.perform(post(path(auth.workspaceId()) + "/" + obligationId + "/mark-paid")
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "walletId", wallet.getId().toString(),
                                "categoryId", category.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactionId").value(transactionId));

        assertThat(transactionRepository.count()).isEqualTo(txBefore + 1);
    }

    @Test
    void alreadyPaidDifferentLinkAndCancelledPaymentAreBlocked() throws Exception {
        AuthContext auth = auth("planned_paid_blocked");
        Workspace workspace = workspace(auth.workspaceId());
        Wallet wallet = wallet(workspace, "Cash");
        Category category = category(workspace, "Rent");
        UUID obligationId = obligation(auth, "Rent", "1000", LocalDate.now().plusDays(3));
        UUID first = expense(auth, wallet, category, "1000", TransactionStatus.POSTED, false);
        UUID second = expense(auth, wallet, category, "1000", TransactionStatus.POSTED, false);

        mockMvc.perform(post(path(auth.workspaceId()) + "/" + obligationId + "/link-transaction")
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(linkJson(first)))
                .andExpect(status().isOk());

        mockMvc.perform(post(path(auth.workspaceId()) + "/" + obligationId + "/link-transaction")
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(linkJson(second)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLANNING_OBLIGATION_ALREADY_PAID"));

        UUID cancelled = obligation(auth, "Cancelled", "1000", LocalDate.now().plusDays(3));
        mockMvc.perform(post(path(auth.workspaceId()) + "/" + cancelled + "/cancel")
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk());
        mockMvc.perform(post(path(auth.workspaceId()) + "/" + cancelled + "/mark-paid")
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "walletId", wallet.getId().toString(),
                                "categoryId", category.getId().toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLANNING_OBLIGATION_CANCELLED"));
    }

    @Test
    void markPaidRequiresSameWorkspaceWalletAndCategory() throws Exception {
        AuthContext auth = auth("planned_mark_invalid");
        AuthContext other = auth("planned_mark_other");
        Workspace workspace = workspace(auth.workspaceId());
        Wallet wallet = wallet(workspace, "Cash");
        Category category = category(workspace, "Rent");
        Wallet otherWallet = wallet(workspace(other.workspaceId()), "Other cash");
        Category otherCategory = category(workspace(other.workspaceId()), "Other rent");
        UUID obligationId = obligation(auth, "Rent", "1000", LocalDate.now().plusDays(3));
        long txBefore = transactionRepository.count();

        mockMvc.perform(post(path(auth.workspaceId()) + "/" + obligationId + "/mark-paid")
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("categoryId", category.getId().toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLANNING_OBLIGATION_WALLET_REQUIRED"));

        mockMvc.perform(post(path(auth.workspaceId()) + "/" + obligationId + "/mark-paid")
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("walletId", wallet.getId().toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLANNING_OBLIGATION_CATEGORY_REQUIRED"));

        mockMvc.perform(post(path(auth.workspaceId()) + "/" + obligationId + "/mark-paid")
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "walletId", otherWallet.getId().toString(),
                                "categoryId", category.getId().toString()))))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(path(auth.workspaceId()) + "/" + obligationId + "/mark-paid")
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "walletId", wallet.getId().toString(),
                                "categoryId", otherCategory.getId().toString()))))
                .andExpect(status().isNotFound());

        assertThat(transactionRepository.count()).isEqualTo(txBefore);
    }

    @Test
    void projectionExcludesPaidObligationAndNoAutoPayOccurs() throws Exception {
        AuthContext auth = auth("planned_projection_paid");
        Workspace workspace = workspace(auth.workspaceId());
        Wallet wallet = wallet(workspace, "Cash");
        Category category = category(workspace, "Rent");
        UUID obligationId = obligation(auth, "Rent", "1000", LocalDate.now().plusDays(3));
        UUID overdueId = obligation(auth, "Overdue", "500", LocalDate.now().minusDays(1));
        long txBefore = transactionRepository.count();

        assertThat(projectionService.calculateProjection(auth.workspaceId(), LocalDate.now(), 30).upcomingRequiredOutflowAmount())
                .isEqualByComparingTo("1000");
        assertThat(transactionRepository.count()).isEqualTo(txBefore);
        assertThat(obligationRepository.findByIdAndWorkspaceIdAndDeletedAtIsNull(overdueId, auth.workspaceId()).orElseThrow().getStatus())
                .isEqualTo(PlannedObligationStatus.PLANNED);

        mockMvc.perform(post(path(auth.workspaceId()) + "/" + obligationId + "/mark-paid")
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "walletId", wallet.getId().toString(),
                                "categoryId", category.getId().toString()))))
                .andExpect(status().isOk());

        assertThat(projectionService.calculateProjection(auth.workspaceId(), LocalDate.now(), 30).upcomingRequiredOutflowAmount())
                .isEqualByComparingTo("0");
    }

    private UUID obligation(AuthContext auth, String name, String amount, LocalDate dueDate) throws Exception {
        String body = mockMvc.perform(post(path(auth.workspaceId()))
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new PlannedObligationRequest(
                                name, new BigDecimal(amount), "VND", dueDate, "REQUIRED", null, null, null, "NONE"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).path("data").path("id").asText());
    }

    private UUID expense(AuthContext auth, Wallet wallet, Category category, String amount, TransactionStatus status, boolean deleted) {
        TransactionResponse response = transactionService.create(auth.workspaceId(), tx(TransactionType.EXPENSE, wallet, category, amount, status), auth.userId());
        if (deleted) {
            Transaction tx = transactionRepository.findByIdAndWorkspaceId(response.getId(), auth.workspaceId()).orElseThrow();
            tx.setDeletedAt(Instant.now());
            transactionRepository.saveAndFlush(tx);
        }
        return response.getId();
    }

    private UUID income(AuthContext auth, Wallet wallet, String amount) {
        return transactionService.create(auth.workspaceId(), tx(TransactionType.INCOME, wallet, null, amount, TransactionStatus.POSTED), auth.userId()).getId();
    }

    private TransactionRequest tx(TransactionType type, Wallet wallet, Category category, String amount, TransactionStatus status) {
        TransactionRequest req = new TransactionRequest();
        req.setType(type);
        req.setStatus(status);
        req.setAmount(new BigDecimal(amount));
        req.setWalletId(wallet.getId());
        if (category != null) req.setCategoryId(category.getId());
        req.setTransactionDate(LocalDate.now());
        return req;
    }

    private String json(PlannedObligationRequest request) {
        return """
                {
                  "name": "%s",
                  "amount": %s,
                  "currency": "%s",
                  "dueDate": "%s",
                  "priority": "%s",
                  "recurrenceType": "%s"
                }
                """.formatted(request.name(), request.amount(), request.currency(), request.dueDate(), request.priority(), request.recurrenceType());
    }

    private String linkJson(UUID transactionId) {
        return "{\"transactionId\":\"" + transactionId + "\"}";
    }

    private AuthContext auth(String username) {
        RegisterRequest request = registerRequest(username);
        UserResponse user = authService.register(request);
        TokenResponse token = authService.login(loginRequest(request.getUsername()));
        Workspace workspace = workspaceRepository.findAllByUserId(user.getId()).get(0);
        return new AuthContext(user.getId(), token.getAccessToken(), workspace.getId());
    }

    private RegisterRequest registerRequest(String username) {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(username + "_" + UUID.randomUUID().toString().substring(0, 8));
        req.setEmail(req.getUsername() + "@example.com");
        req.setPassword("StrongPassword123");
        req.setFullName("Planned Obligation Payment Test");
        return req;
    }

    private LoginRequest loginRequest(String username) {
        LoginRequest req = new LoginRequest();
        req.setIdentifier(username);
        req.setPassword("StrongPassword123");
        return req;
    }

    private Workspace workspace(UUID id) {
        return workspaceRepository.findById(id).orElseThrow();
    }

    private Wallet wallet(Workspace workspace, String name) {
        return walletRepository.saveAndFlush(Wallet.builder()
                .workspace(workspace)
                .name(name)
                .walletType(WalletType.CASH)
                .openingBalance(BigDecimal.ZERO)
                .openingDate(LocalDate.now().minusDays(30))
                .isActive(true)
                .includeInTotal(true)
                .build());
    }

    private Category category(Workspace workspace, String name) {
        Jar jar = jarRepository.saveAndFlush(Jar.builder()
                .workspace(workspace)
                .code("PAY" + UUID.randomUUID().toString().substring(0, 4))
                .name("Payment")
                .isActive(true)
                .build());
        return categoryRepository.saveAndFlush(Category.builder()
                .workspace(workspace)
                .jar(jar)
                .name(name)
                .categoryType(CategoryType.EXPENSE)
                .isActive(true)
                .isArchived(false)
                .build());
    }

    private String path(UUID workspaceId) {
        return "/api/workspaces/" + workspaceId + "/planning/obligations";
    }

    private String bearer(AuthContext auth) {
        return "Bearer " + auth.accessToken();
    }

    private record AuthContext(UUID userId, String accessToken, UUID workspaceId) {
    }
}
