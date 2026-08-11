package com.moneyflowbackend;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.moneyflowbackend.jar.model.Jar;
import com.moneyflowbackend.jar.repository.JarRepository;
import com.moneyflowbackend.planning.dto.CancelPlannedObligationRequest;
import com.moneyflowbackend.planning.dto.PlannedObligationRequest;
import com.moneyflowbackend.planning.dto.PlannedObligationUpdateRequest;
import com.moneyflowbackend.planning.model.PlannedObligation;
import com.moneyflowbackend.planning.model.PlannedObligationStatus;
import com.moneyflowbackend.planning.repository.PlannedObligationRepository;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.model.WalletType;
import com.moneyflowbackend.wallet.repository.WalletRepository;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PlannedObligationApiIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired JarRepository jarRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired PlannedObligationRepository obligationRepository;
    @Autowired TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createValidObligationWithWorkspaceRefsDoesNotCreateTransaction() throws Exception {
        AuthContext auth = auth("planned_create");
        Workspace workspace = workspace(auth.workspaceId());
        Wallet wallet = wallet(workspace, "Cash");
        Category category = category(workspace, "Rent");
        long txBefore = transactionRepository.count();

        mockMvc.perform(post(path(auth.workspaceId()))
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new PlannedObligationRequest(
                                " Rent ", new BigDecimal("2500000"), null, LocalDate.now().plusDays(30),
                                "REQUIRED", wallet.getId(), category.getId(), "Monthly rent", "MONTHLY"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Rent"))
                .andExpect(jsonPath("$.data.amount").value(2500000))
                .andExpect(jsonPath("$.data.currency").value("VND"))
                .andExpect(jsonPath("$.data.status").value("PLANNED"))
                .andExpect(jsonPath("$.data.priority").value("REQUIRED"))
                .andExpect(jsonPath("$.data.recurrenceType").value("MONTHLY"))
                .andExpect(jsonPath("$.data.walletId").value(wallet.getId().toString()))
                .andExpect(jsonPath("$.data.categoryId").value(category.getId().toString()))
                .andExpect(jsonPath("$.data.jarId").value(category.getJar().getId().toString()));

        assertThat(transactionRepository.count()).isEqualTo(txBefore);
        assertThat(obligationRepository.search(auth.workspaceId(), null, null, null, null, true, org.springframework.data.domain.PageRequest.of(0, 10))).hasSize(1);
    }

    @Test
    void createValidationRejectsInvalidAmountAndMissingDueDate() throws Exception {
        AuthContext auth = auth("planned_invalid");

        mockMvc.perform(post(path(auth.workspaceId()))
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Rent",
                                "amount", 0,
                                "dueDate", LocalDate.now().plusDays(1).toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLANNED_OBLIGATION_AMOUNT_INVALID"));

        mockMvc.perform(post(path(auth.workspaceId()))
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Rent",
                                "amount", 1000))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLANNED_OBLIGATION_DUE_DATE_REQUIRED"));

        assertThat(obligationRepository.search(auth.workspaceId(), null, null, null, null, true, org.springframework.data.domain.PageRequest.of(0, 10))).isEmpty();
    }

    @Test
    void createRejectsWalletOrCategoryFromOtherWorkspace() throws Exception {
        AuthContext owner = auth("planned_owner");
        AuthContext other = auth("planned_other");
        Wallet otherWallet = wallet(workspace(other.workspaceId()), "Other cash");
        Category otherCategory = category(workspace(other.workspaceId()), "Other rent");

        mockMvc.perform(post(path(owner.workspaceId()))
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new PlannedObligationRequest(
                                "Rent", new BigDecimal("1000"), "VND", LocalDate.now().plusDays(1),
                                null, otherWallet.getId(), null, null, null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));

        mockMvc.perform(post(path(owner.workspaceId()))
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new PlannedObligationRequest(
                                "Rent", new BigDecimal("1000"), "VND", LocalDate.now().plusDays(1),
                                null, null, otherCategory.getId(), null, null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    void listFiltersDateRangeAndHidesCancelledByDefault() throws Exception {
        AuthContext auth = auth("planned_list");
        UUID workspaceId = auth.workspaceId();
        UUID inside = create(auth, "Inside", LocalDate.now().plusDays(2));
        create(auth, "Outside", LocalDate.now().plusDays(40));
        UUID cancelled = create(auth, "Cancelled", LocalDate.now().plusDays(3));
        cancel(auth, cancelled);

        mockMvc.perform(get(path(workspaceId))
                        .header("Authorization", bearer(auth))
                        .param("from", LocalDate.now().toString())
                        .param("to", LocalDate.now().plusDays(7).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1))
                .andExpect(jsonPath("$.data.obligations[0].id").value(inside.toString()));

        mockMvc.perform(get(path(workspaceId))
                        .header("Authorization", bearer(auth))
                        .param("includeCancelled", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.obligations.length()").value(3))
                .andExpect(jsonPath("$.data.obligations[0].dueDate").value(LocalDate.now().plusDays(2).toString()));
    }

    @Test
    void detailUpdateAndCancelDoNotCreateTransactions() throws Exception {
        AuthContext auth = auth("planned_update");
        UUID obligationId = create(auth, "Rent", LocalDate.now().plusDays(10));
        long txBefore = transactionRepository.count();

        mockMvc.perform(get(path(auth.workspaceId()) + "/" + obligationId)
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(obligationId.toString()));

        mockMvc.perform(patch(path(auth.workspaceId()) + "/" + obligationId)
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new PlannedObligationUpdateRequest(
                                "Updated rent", new BigDecimal("3000000"), "VND", LocalDate.now().plusDays(12),
                                "IMPORTANT", null, null, "Updated note", "NONE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated rent"))
                .andExpect(jsonPath("$.data.priority").value("IMPORTANT"));

        mockMvc.perform(post(path(auth.workspaceId()) + "/" + obligationId + "/cancel")
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CancelPlannedObligationRequest("No longer needed"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.computedState").value("CANCELLED"));

        mockMvc.perform(post(path(auth.workspaceId()) + "/" + obligationId + "/cancel")
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        assertThat(transactionRepository.count()).isEqualTo(txBefore);
    }

    @Test
    void cancelPaidObligationIsBlocked() throws Exception {
        AuthContext auth = auth("planned_paid");
        UUID obligationId = create(auth, "Rent", LocalDate.now().plusDays(1));
        PlannedObligation obligation = obligationRepository.findByIdAndWorkspaceIdAndDeletedAtIsNull(obligationId, auth.workspaceId()).orElseThrow();
        obligation.setStatus(PlannedObligationStatus.PAID);
        obligationRepository.saveAndFlush(obligation);

        mockMvc.perform(post(path(auth.workspaceId()) + "/" + obligationId + "/cancel")
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLANNED_OBLIGATION_PAID"));
    }

    @Test
    void computedStateHandlesUpcomingDueSoonAndOverdue() throws Exception {
        AuthContext auth = auth("planned_state");
        create(auth, "Overdue", LocalDate.now().minusDays(1));
        create(auth, "Due soon", LocalDate.now().plusDays(3));
        create(auth, "Upcoming", LocalDate.now().plusDays(20));

        mockMvc.perform(get(path(auth.workspaceId()))
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.obligations[0].computedState").value("OVERDUE"))
                .andExpect(jsonPath("$.data.obligations[1].computedState").value("DUE_SOON"))
                .andExpect(jsonPath("$.data.obligations[2].computedState").value("UPCOMING"));
    }

    @Test
    void workspaceIsolationBlocksReadUpdateAndCancel() throws Exception {
        AuthContext owner = auth("planned_iso_owner");
        AuthContext other = auth("planned_iso_other");
        UUID obligationId = create(owner, "Rent", LocalDate.now().plusDays(1));

        mockMvc.perform(get(path(other.workspaceId()) + "/" + obligationId)
                        .header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(path(owner.workspaceId()) + "/" + obligationId)
                        .header("Authorization", bearer(other)))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch(path(owner.workspaceId()) + "/" + obligationId)
                        .header("Authorization", bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Hacked"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(path(owner.workspaceId()) + "/" + obligationId + "/cancel")
                        .header("Authorization", bearer(other)))
                .andExpect(status().isForbidden());
    }

    @Test
    void recurrenceStoredButDoesNotGenerateExtraRows() throws Exception {
        AuthContext auth = auth("planned_recurrence");
        long before = obligationRepository.count();

        mockMvc.perform(post(path(auth.workspaceId()))
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new PlannedObligationRequest(
                                "Rent", new BigDecimal("1000"), "VND", LocalDate.now().plusDays(1),
                                null, null, null, null, "YEARLY"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recurrenceType").value("YEARLY"));

        assertThat(obligationRepository.count()).isEqualTo(before + 1);
    }

    private UUID create(AuthContext auth, String name, LocalDate dueDate) throws Exception {
        String body = mockMvc.perform(post(path(auth.workspaceId()))
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new PlannedObligationRequest(
                                name, new BigDecimal("1000"), "VND", dueDate, "REQUIRED", null, null, null, "NONE"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).path("data").path("id").asText());
    }

    private void cancel(AuthContext auth, UUID obligationId) throws Exception {
        mockMvc.perform(post(path(auth.workspaceId()) + "/" + obligationId + "/cancel")
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk());
    }

    private String json(PlannedObligationRequest request) {
        return """
                {
                  "name": "%s",
                  "amount": %s,
                  "currency": %s,
                  "dueDate": "%s",
                  "priority": %s,
                  "walletId": %s,
                  "categoryId": %s,
                  "note": %s,
                  "recurrenceType": %s
                }
                """.formatted(
                request.name(),
                request.amount(),
                nullable(request.currency()),
                request.dueDate(),
                nullable(request.priority()),
                nullable(request.walletId()),
                nullable(request.categoryId()),
                nullable(request.note()),
                nullable(request.recurrenceType()));
    }

    private String json(PlannedObligationUpdateRequest request) {
        return """
                {
                  "name": "%s",
                  "amount": %s,
                  "currency": "%s",
                  "dueDate": "%s",
                  "priority": "%s",
                  "note": "%s",
                  "recurrenceType": "%s"
                }
                """.formatted(request.name(), request.amount(), request.currency(), request.dueDate(), request.priority(), request.note(), request.recurrenceType());
    }

    private String json(CancelPlannedObligationRequest request) {
        return "{\"reason\":\"" + request.reason() + "\"}";
    }

    private String nullable(Object value) {
        return value == null ? "null" : "\"" + value + "\"";
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
        req.setFullName("Planned Obligation Test");
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
                .code("NEC" + UUID.randomUUID().toString().substring(0, 4))
                .name("Needs")
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
