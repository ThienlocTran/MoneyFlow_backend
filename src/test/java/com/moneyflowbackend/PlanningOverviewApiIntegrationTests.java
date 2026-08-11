package com.moneyflowbackend;

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
import com.moneyflowbackend.planning.model.PlannedObligation;
import com.moneyflowbackend.planning.model.PlannedObligationPriority;
import com.moneyflowbackend.planning.model.PlannedObligationStatus;
import com.moneyflowbackend.planning.model.PlanningRecurrenceType;
import com.moneyflowbackend.planning.model.ReserveAllocation;
import com.moneyflowbackend.planning.model.ReserveAllocationStatus;
import com.moneyflowbackend.planning.model.ReservePurposeType;
import com.moneyflowbackend.planning.repository.PlannedObligationRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PlanningOverviewApiIntegrationTests {
    private static final LocalDate AS_OF = LocalDate.parse("2026-08-11");

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired JarRepository jarRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired PlannedObligationRepository obligationRepository;
    @Autowired ReserveAllocationRepository reserveRepository;
    @Autowired TransactionRepository transactionRepository;

    @Test
    void overviewHappyPathReturnsProjectionSummariesListsAndActionItems() throws Exception {
        AuthContext auth = auth("planning_overview");
        Workspace workspace = workspace(auth.workspaceId());
        Wallet wallet = wallet(workspace, "Cash", "5000000");
        Category category = category(workspace, "Rent");
        reserve(workspace, "Rent reserve", "1000000", ReserveAllocationStatus.ACTIVE, wallet, category);
        obligation(workspace, "Rent", "2000000", AS_OF.plusDays(5), PlannedObligationStatus.PLANNED, PlannedObligationPriority.REQUIRED, wallet, category);
        obligation(workspace, "Old bill", "300000", AS_OF.minusDays(1), PlannedObligationStatus.PLANNED, PlannedObligationPriority.IMPORTANT, wallet, category);

        mockMvc.perform(get(path(auth.workspaceId()) + "/overview")
                        .header("Authorization", bearer(auth))
                        .param("asOfDate", AS_OF.toString())
                        .param("horizonDays", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projection.availableLedgerBalance").value(5000000))
                .andExpect(jsonPath("$.data.projection.activeReserveAmount").value(1000000))
                .andExpect(jsonPath("$.data.projection.upcomingRequiredOutflowAmount").value(2000000))
                .andExpect(jsonPath("$.data.projection.overdueRequiredOutflowAmount").value(300000))
                .andExpect(jsonPath("$.data.projection.actuallySpendable").value(1700000))
                .andExpect(jsonPath("$.data.obligationSummary.upcomingCount").value(1))
                .andExpect(jsonPath("$.data.obligationSummary.overdueCount").value(1))
                .andExpect(jsonPath("$.data.reserveSummary.activeCount").value(1))
                .andExpect(jsonPath("$.data.upcomingObligations[0].computedState").value("DUE_SOON"))
                .andExpect(jsonPath("$.data.overdueObligations[0].computedState").value("OVERDUE"))
                .andExpect(jsonPath("$.data.activeReserves[0].purposeType").value("CUSTOM"))
                .andExpect(jsonPath("$.data.actionItems[*].type", hasItem("PLANNING_OBLIGATION_DUE_SOON")))
                .andExpect(jsonPath("$.data.actionItems[*].type", hasItem("PLANNING_OBLIGATION_OVERDUE")))
                .andExpect(jsonPath("$.data.actionItems[*].type", hasItem("ACTIVE_RESERVE_SUMMARY")));
    }

    @Test
    void projectionEndpointMapsSnapshot() throws Exception {
        AuthContext auth = auth("planning_projection");
        Workspace workspace = workspace(auth.workspaceId());
        wallet(workspace, "Cash", "1000000");

        mockMvc.perform(get(path(auth.workspaceId()) + "/projection")
                        .header("Authorization", bearer(auth))
                        .param("asOfDate", AS_OF.toString())
                        .param("horizonDays", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workspaceId").value(auth.workspaceId().toString()))
                .andExpect(jsonPath("$.data.currency").value("VND"))
                .andExpect(jsonPath("$.data.availableLedgerBalance").value(1000000))
                .andExpect(jsonPath("$.data.expectedIncomingAmount").value(0));
    }

    @Test
    void summariesCountObligationsAndReservesByContract() throws Exception {
        AuthContext auth = auth("planning_summaries");
        Workspace workspace = workspace(auth.workspaceId());
        Wallet wallet = wallet(workspace, "Cash", "1000000");
        Category category = category(workspace, "Rent");
        obligation(workspace, "Upcoming", "100", AS_OF.plusDays(10), PlannedObligationStatus.PLANNED, PlannedObligationPriority.REQUIRED, wallet, category);
        obligation(workspace, "Overdue", "200", AS_OF.minusDays(1), PlannedObligationStatus.PLANNED, PlannedObligationPriority.REQUIRED, wallet, category);
        obligation(workspace, "Paid", "300", AS_OF.plusDays(2), PlannedObligationStatus.PAID, PlannedObligationPriority.REQUIRED, wallet, category);
        obligation(workspace, "Cancelled", "400", AS_OF.plusDays(2), PlannedObligationStatus.CANCELLED, PlannedObligationPriority.REQUIRED, wallet, category);
        reserve(workspace, "Active", "1000", ReserveAllocationStatus.ACTIVE, wallet, category);
        reserve(workspace, "Released", "2000", ReserveAllocationStatus.RELEASED, wallet, category);
        reserve(workspace, "Cancelled", "3000", ReserveAllocationStatus.CANCELLED, wallet, category);

        mockMvc.perform(get(path(auth.workspaceId()) + "/obligations/summary")
                        .header("Authorization", bearer(auth))
                        .param("asOfDate", AS_OF.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.upcomingAmount").value(100))
                .andExpect(jsonPath("$.data.overdueAmount").value(200))
                .andExpect(jsonPath("$.data.upcomingCount").value(1))
                .andExpect(jsonPath("$.data.overdueCount").value(1))
                .andExpect(jsonPath("$.data.paidCount").value(1))
                .andExpect(jsonPath("$.data.cancelledCount").value(1));

        mockMvc.perform(get(path(auth.workspaceId()) + "/reserves/summary")
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeAmount").value(1000))
                .andExpect(jsonPath("$.data.releasedAmount").value(0));

        mockMvc.perform(get(path(auth.workspaceId()) + "/reserves/summary")
                        .header("Authorization", bearer(auth))
                        .param("includeInactive", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeAmount").value(1000))
                .andExpect(jsonPath("$.data.releasedAmount").value(2000))
                .andExpect(jsonPath("$.data.cancelledAmount").value(3000));
    }

    @Test
    void shortfallLowSpendableAndPartialDataActionItemsAreMapped() throws Exception {
        AuthContext shortfall = auth("planning_shortfall");
        Workspace shortfallWorkspace = workspace(shortfall.workspaceId());
        Wallet wallet = wallet(shortfallWorkspace, "Cash", "100000");
        Category category = category(shortfallWorkspace, "Rent");
        obligation(shortfallWorkspace, "Rent", "800000", AS_OF.plusDays(1), PlannedObligationStatus.PLANNED, PlannedObligationPriority.REQUIRED, wallet, category);

        mockMvc.perform(get(path(shortfall.workspaceId()) + "/overview")
                        .header("Authorization", bearer(shortfall))
                        .param("asOfDate", AS_OF.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actionItems[*].type", hasItem("PROJECTED_SHORTFALL")));

        AuthContext low = auth("planning_low");
        wallet(workspace(low.workspaceId()), "Cash", "300000");
        mockMvc.perform(get(path(low.workspaceId()) + "/overview")
                        .header("Authorization", bearer(low))
                        .param("asOfDate", AS_OF.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actionItems[*].type", hasItem("LOW_ACTUALLY_SPENDABLE")));

        AuthContext empty = auth("planning_partial");
        walletRepository.findAllByWorkspaceIdOrderByCreatedAtAsc(empty.workspaceId()).forEach(existingWallet -> {
            existingWallet.setActive(false);
            walletRepository.saveAndFlush(existingWallet);
        });
        mockMvc.perform(get(path(empty.workspaceId()) + "/overview")
                        .header("Authorization", bearer(empty))
                        .param("asOfDate", AS_OF.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actionItems[*].type", hasItem("PARTIAL_PLANNING_DATA")));
    }

    @Test
    void invalidParamsReturnCleanBadRequest() throws Exception {
        AuthContext auth = auth("planning_invalid");

        mockMvc.perform(get(path(auth.workspaceId()) + "/overview")
                        .header("Authorization", bearer(auth))
                        .param("horizonDays", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PLANNING_HORIZON_DAYS"));

        mockMvc.perform(get(path(auth.workspaceId()) + "/overview")
                        .header("Authorization", bearer(auth))
                        .param("asOfDate", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PLANNING_DATE"));
    }

    @Test
    void workspaceIsolationAndReadOnlyNoSideEffects() throws Exception {
        AuthContext owner = auth("planning_iso_owner");
        AuthContext other = auth("planning_iso_other");
        Workspace ownerWorkspace = workspace(owner.workspaceId());
        Workspace otherWorkspace = workspace(other.workspaceId());
        Wallet ownerWallet = wallet(ownerWorkspace, "Cash", "1000000");
        Category ownerCategory = category(ownerWorkspace, "Rent");
        Wallet otherWallet = wallet(otherWorkspace, "Other cash", "9000000");
        Category otherCategory = category(otherWorkspace, "Other rent");
        PlannedObligation ownerObligation = obligation(ownerWorkspace, "Owner rent", "1000", AS_OF.plusDays(1), PlannedObligationStatus.PLANNED, PlannedObligationPriority.REQUIRED, ownerWallet, ownerCategory);
        obligation(otherWorkspace, "Other rent", "9000000", AS_OF.plusDays(1), PlannedObligationStatus.PLANNED, PlannedObligationPriority.REQUIRED, otherWallet, otherCategory);
        ReserveAllocation ownerReserve = reserve(ownerWorkspace, "Owner reserve", "1000", ReserveAllocationStatus.ACTIVE, ownerWallet, ownerCategory);
        reserve(otherWorkspace, "Other reserve", "9000000", ReserveAllocationStatus.ACTIVE, otherWallet, otherCategory);
        long txBefore = transactionRepository.count();
        long obligationsBefore = obligationRepository.count();
        long reservesBefore = reserveRepository.count();

        mockMvc.perform(get(path(owner.workspaceId()) + "/overview")
                        .header("Authorization", bearer(owner))
                        .param("asOfDate", AS_OF.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projection.upcomingRequiredOutflowAmount").value(1000))
                .andExpect(jsonPath("$.data.projection.activeReserveAmount").value(1000));

        mockMvc.perform(get(path(owner.workspaceId()) + "/overview")
                        .header("Authorization", bearer(other)))
                .andExpect(status().isForbidden());

        assertThat(transactionRepository.count()).isEqualTo(txBefore);
        assertThat(obligationRepository.count()).isEqualTo(obligationsBefore);
        assertThat(reserveRepository.count()).isEqualTo(reservesBefore);
        assertThat(obligationRepository.findById(ownerObligation.getId()).orElseThrow().getStatus()).isEqualTo(PlannedObligationStatus.PLANNED);
        assertThat(reserveRepository.findById(ownerReserve.getId()).orElseThrow().getStatus()).isEqualTo(ReserveAllocationStatus.ACTIVE);
    }

    @Test
    void emptyStateReturnsZeroSummary() throws Exception {
        AuthContext auth = auth("planning_empty");

        mockMvc.perform(get(path(auth.workspaceId()) + "/overview")
                        .header("Authorization", bearer(auth))
                        .param("asOfDate", AS_OF.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.obligationSummary.upcomingCount").value(0))
                .andExpect(jsonPath("$.data.reserveSummary.activeCount").value(0))
                .andExpect(jsonPath("$.data.upcomingObligations.length()").value(0))
                .andExpect(jsonPath("$.data.activeReserves.length()").value(0));
    }

    private PlannedObligation obligation(Workspace workspace, String name, String amount, LocalDate dueDate,
                                         PlannedObligationStatus status, PlannedObligationPriority priority,
                                         Wallet wallet, Category category) {
        return obligationRepository.saveAndFlush(PlannedObligation.builder()
                .workspace(workspace)
                .createdByUser(workspace.getCreatedByUser())
                .name(name)
                .amount(new BigDecimal(amount))
                .currency("VND")
                .dueDate(dueDate)
                .status(status)
                .priority(priority)
                .recurrenceType(PlanningRecurrenceType.NONE)
                .wallet(wallet)
                .category(category)
                .build());
    }

    private ReserveAllocation reserve(Workspace workspace, String name, String amount, ReserveAllocationStatus status,
                                      Wallet wallet, Category category) {
        return reserveRepository.saveAndFlush(ReserveAllocation.builder()
                .workspace(workspace)
                .createdByUser(workspace.getCreatedByUser())
                .name(name)
                .amount(new BigDecimal(amount))
                .currency("VND")
                .status(status)
                .purposeType(ReservePurposeType.CUSTOM)
                .wallet(wallet)
                .category(category)
                .targetDate(AS_OF.plusDays(30))
                .build());
    }

    private Wallet wallet(Workspace workspace, String name, String openingBalance) {
        return walletRepository.saveAndFlush(Wallet.builder()
                .workspace(workspace)
                .name(name)
                .walletType(WalletType.CASH)
                .openingBalance(new BigDecimal(openingBalance))
                .openingDate(AS_OF.minusDays(30))
                .isActive(true)
                .includeInTotal(true)
                .build());
    }

    private Category category(Workspace workspace, String name) {
        Jar jar = jarRepository.saveAndFlush(Jar.builder()
                .workspace(workspace)
                .code("OVR" + UUID.randomUUID().toString().substring(0, 4))
                .name("Overview")
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
        req.setFullName("Planning Overview Test");
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

    private String path(UUID workspaceId) {
        return "/api/workspaces/" + workspaceId + "/planning";
    }

    private String bearer(AuthContext auth) {
        return "Bearer " + auth.accessToken();
    }

    private record AuthContext(UUID userId, String accessToken, UUID workspaceId) {
    }
}
