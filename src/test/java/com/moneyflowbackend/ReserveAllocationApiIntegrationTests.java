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
import com.moneyflowbackend.planning.dto.ReserveAllocationActionRequest;
import com.moneyflowbackend.planning.dto.ReserveAllocationRequest;
import com.moneyflowbackend.planning.dto.ReserveAllocationUpdateRequest;
import com.moneyflowbackend.planning.model.ReserveAllocation;
import com.moneyflowbackend.planning.model.ReserveAllocationStatus;
import com.moneyflowbackend.planning.repository.ReserveAllocationRepository;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
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
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReserveAllocationApiIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired JarRepository jarRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ReserveAllocationRepository reserveRepository;
    @Autowired TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createValidReserveWithWorkspaceRefsDoesNotCreateTransaction() throws Exception {
        AuthContext auth = auth("reserve_create");
        Workspace workspace = workspace(auth.workspaceId());
        Wallet wallet = wallet(workspace, "Cash");
        Jar jar = jar(workspace, "NEEDS");
        Category category = category(workspace, jar, "Food");
        long txBefore = transactionRepository.count();

        mockMvc.perform(post(path(auth.workspaceId()))
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ReserveAllocationRequest(
                                " Emergency fund ", new BigDecimal("2000000"), null, "EMERGENCY_FUND",
                                wallet.getId(), category.getId(), jar.getId(), LocalDate.now().plusDays(30), "Hold"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Emergency fund"))
                .andExpect(jsonPath("$.data.amount").value(2000000))
                .andExpect(jsonPath("$.data.currency").value("VND"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.purposeType").value("EMERGENCY_FUND"))
                .andExpect(jsonPath("$.data.walletId").value(wallet.getId().toString()))
                .andExpect(jsonPath("$.data.categoryId").value(category.getId().toString()))
                .andExpect(jsonPath("$.data.jarId").value(jar.getId().toString()))
                .andExpect(jsonPath("$.data.warnings[0]").value("RESERVE_BALANCE_CHECK_UNAVAILABLE"));

        assertThat(transactionRepository.count()).isEqualTo(txBefore);
        assertThat(reserveRepository.search(auth.workspaceId(), null, null, true, null, null,
                org.springframework.data.domain.PageRequest.of(0, 10))).hasSize(1);
    }

    @Test
    void createValidationRejectsInvalidAmountAndDefaultsPurpose() throws Exception {
        AuthContext auth = auth("reserve_invalid");

        mockMvc.perform(post(path(auth.workspaceId()))
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Emergency",
                                "amount", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESERVE_AMOUNT_INVALID"));

        mockMvc.perform(post(path(auth.workspaceId()))
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ReserveAllocationRequest(
                                "Emergency", new BigDecimal("1000"), "VND", null,
                                null, null, null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.purposeType").value("CUSTOM"));
    }

    @Test
    void createRejectsWorkspaceForeignRefs() throws Exception {
        AuthContext owner = auth("reserve_owner");
        AuthContext other = auth("reserve_other");
        Workspace otherWorkspace = workspace(other.workspaceId());
        Wallet otherWallet = wallet(otherWorkspace, "Other cash");
        Jar otherJar = jar(otherWorkspace, "OTHER");
        Category otherCategory = category(otherWorkspace, otherJar, "Other food");

        mockMvc.perform(post(path(owner.workspaceId()))
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ReserveAllocationRequest(
                                "Emergency", new BigDecimal("1000"), "VND", null,
                                otherWallet.getId(), null, null, null, null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVE_WALLET_NOT_FOUND"));

        mockMvc.perform(post(path(owner.workspaceId()))
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ReserveAllocationRequest(
                                "Emergency", new BigDecimal("1000"), "VND", null,
                                null, otherCategory.getId(), null, null, null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVE_CATEGORY_NOT_FOUND"));

        mockMvc.perform(post(path(owner.workspaceId()))
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ReserveAllocationRequest(
                                "Emergency", new BigDecimal("1000"), "VND", null,
                                null, null, otherJar.getId(), null, null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVE_JAR_NOT_FOUND"));
    }

    @Test
    void listDefaultsActiveAndIncludeInactiveReturnsAll() throws Exception {
        AuthContext auth = auth("reserve_list");
        UUID active = create(auth, "Active", LocalDate.now().plusDays(5));
        UUID released = create(auth, "Released", LocalDate.now().plusDays(3));
        UUID cancelled = create(auth, "Cancelled", LocalDate.now().plusDays(4));
        release(auth, released);
        cancel(auth, cancelled);

        mockMvc.perform(get(path(auth.workspaceId()))
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1))
                .andExpect(jsonPath("$.data.reserves[0].id").value(active.toString()))
                .andExpect(jsonPath("$.data.totalActiveAmount").value(1000));

        mockMvc.perform(get(path(auth.workspaceId()))
                        .header("Authorization", bearer(auth))
                        .param("includeInactive", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(3))
                .andExpect(jsonPath("$.data.reserves[0].id").value(released.toString()))
                .andExpect(jsonPath("$.data.warnings[0]").value("RESERVE_BALANCE_CHECK_UNAVAILABLE"));
    }

    @Test
    void detailAndUpdateActiveReserveDoNotCreateTransactions() throws Exception {
        AuthContext auth = auth("reserve_update");
        UUID reserveId = create(auth, "Emergency", LocalDate.now().plusDays(10));
        long txBefore = transactionRepository.count();

        mockMvc.perform(get(path(auth.workspaceId()) + "/" + reserveId)
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(reserveId.toString()));

        mockMvc.perform(patch(path(auth.workspaceId()) + "/" + reserveId)
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ReserveAllocationUpdateRequest(
                                "Rent reserve", new BigDecimal("3000"), "VND", "RENT",
                                null, null, null, LocalDate.now().plusDays(20), "Updated"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Rent reserve"))
                .andExpect(jsonPath("$.data.amount").value(3000))
                .andExpect(jsonPath("$.data.purposeType").value("RENT"));

        assertThat(transactionRepository.count()).isEqualTo(txBefore);
    }

    @Test
    void inactiveReserveCannotBeUpdated() throws Exception {
        AuthContext auth = auth("reserve_inactive_update");
        UUID released = create(auth, "Released", LocalDate.now().plusDays(1));
        UUID cancelled = create(auth, "Cancelled", LocalDate.now().plusDays(2));
        release(auth, released);
        cancel(auth, cancelled);

        mockMvc.perform(patch(path(auth.workspaceId()) + "/" + released)
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Nope"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESERVE_CANNOT_UPDATE_INACTIVE"));

        mockMvc.perform(patch(path(auth.workspaceId()) + "/" + cancelled)
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Nope"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESERVE_CANNOT_UPDATE_INACTIVE"));
    }

    @Test
    void releaseAndCancelFlowsAreSafeAndIdempotent() throws Exception {
        AuthContext auth = auth("reserve_actions");
        UUID toRelease = create(auth, "Release", LocalDate.now().plusDays(1));
        UUID toCancel = create(auth, "Cancel", LocalDate.now().plusDays(2));
        long txBefore = transactionRepository.count();

        mockMvc.perform(post(path(auth.workspaceId()) + "/" + toRelease + "/release")
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ReserveAllocationActionRequest("Used", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RELEASED"))
                .andExpect(jsonPath("$.data.releasedAt").exists());

        mockMvc.perform(post(path(auth.workspaceId()) + "/" + toRelease + "/release")
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RELEASED"));

        mockMvc.perform(post(path(auth.workspaceId()) + "/" + toRelease + "/cancel")
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESERVE_RELEASED_CANNOT_CANCEL"));

        mockMvc.perform(post(path(auth.workspaceId()) + "/" + toCancel + "/cancel")
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ReserveAllocationActionRequest(null, "No need"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelledAt").exists());

        mockMvc.perform(post(path(auth.workspaceId()) + "/" + toCancel + "/cancel")
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        mockMvc.perform(post(path(auth.workspaceId()) + "/" + toCancel + "/release")
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESERVE_CANCELLED_CANNOT_RELEASE"));

        assertThat(transactionRepository.count()).isEqualTo(txBefore);
    }

    @Test
    void workspaceIsolationBlocksReadUpdateReleaseAndCancel() throws Exception {
        AuthContext owner = auth("reserve_iso_owner");
        AuthContext other = auth("reserve_iso_other");
        UUID reserveId = create(owner, "Emergency", LocalDate.now().plusDays(1));

        mockMvc.perform(get(path(other.workspaceId()) + "/" + reserveId)
                        .header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(path(owner.workspaceId()) + "/" + reserveId)
                        .header("Authorization", bearer(other)))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch(path(owner.workspaceId()) + "/" + reserveId)
                        .header("Authorization", bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Hacked"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(path(owner.workspaceId()) + "/" + reserveId + "/release")
                        .header("Authorization", bearer(other)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(path(owner.workspaceId()) + "/" + reserveId + "/cancel")
                        .header("Authorization", bearer(other)))
                .andExpect(status().isForbidden());
    }

    @Test
    void statusPurposeAndTargetDateFiltersWork() throws Exception {
        AuthContext auth = auth("reserve_filters");
        UUID rent = create(auth, "Rent", LocalDate.now().plusDays(4), "RENT");
        create(auth, "Emergency", LocalDate.now().plusDays(20), "EMERGENCY_FUND");

        mockMvc.perform(get(path(auth.workspaceId()))
                        .header("Authorization", bearer(auth))
                        .param("purposeType", "RENT")
                        .param("fromTargetDate", LocalDate.now().toString())
                        .param("toTargetDate", LocalDate.now().plusDays(7).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1))
                .andExpect(jsonPath("$.data.reserves[0].id").value(rent.toString()));
    }

    private UUID create(AuthContext auth, String name, LocalDate targetDate) throws Exception {
        return create(auth, name, targetDate, "CUSTOM");
    }

    private UUID create(AuthContext auth, String name, LocalDate targetDate, String purposeType) throws Exception {
        String body = mockMvc.perform(post(path(auth.workspaceId()))
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ReserveAllocationRequest(
                                name, new BigDecimal("1000"), "VND", purposeType,
                                null, null, null, targetDate, null))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).path("data").path("id").asText());
    }

    private void release(AuthContext auth, UUID reserveId) throws Exception {
        mockMvc.perform(post(path(auth.workspaceId()) + "/" + reserveId + "/release")
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk());
    }

    private void cancel(AuthContext auth, UUID reserveId) throws Exception {
        mockMvc.perform(post(path(auth.workspaceId()) + "/" + reserveId + "/cancel")
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk());
    }

    private String json(ReserveAllocationRequest request) {
        return """
                {
                  "name": "%s",
                  "amount": %s,
                  "currency": %s,
                  "purposeType": %s,
                  "walletId": %s,
                  "categoryId": %s,
                  "jarId": %s,
                  "targetDate": %s,
                  "note": %s
                }
                """.formatted(
                request.name(),
                request.amount(),
                nullable(request.currency()),
                nullable(request.purposeType()),
                nullable(request.walletId()),
                nullable(request.categoryId()),
                nullable(request.jarId()),
                nullable(request.targetDate()),
                nullable(request.note()));
    }

    private String json(ReserveAllocationUpdateRequest request) {
        return """
                {
                  "name": "%s",
                  "amount": %s,
                  "currency": "%s",
                  "purposeType": "%s",
                  "targetDate": "%s",
                  "note": "%s"
                }
                """.formatted(request.name(), request.amount(), request.currency(), request.purposeType(), request.targetDate(), request.note());
    }

    private String json(ReserveAllocationActionRequest request) {
        return """
                {
                  "note": %s,
                  "reason": %s
                }
                """.formatted(nullable(request.note()), nullable(request.reason()));
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
        req.setFullName("Reserve Allocation Test");
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

    private Jar jar(Workspace workspace, String name) {
        return jarRepository.saveAndFlush(Jar.builder()
                .workspace(workspace)
                .code(name + UUID.randomUUID().toString().substring(0, 4))
                .name(name)
                .isActive(true)
                .build());
    }

    private Category category(Workspace workspace, Jar jar, String name) {
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
        return "/api/workspaces/" + workspaceId + "/planning/reserves";
    }

    private String bearer(AuthContext auth) {
        return "Bearer " + auth.accessToken();
    }

    private record AuthContext(UUID userId, String accessToken, UUID workspaceId) {
    }
}
