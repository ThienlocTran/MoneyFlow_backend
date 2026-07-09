package com.moneyflowbackend;

import com.jayway.jsonpath.JsonPath;
import com.moneyflowbackend.auth.dto.LoginRequest;
import com.moneyflowbackend.auth.dto.RegisterRequest;
import com.moneyflowbackend.auth.dto.TokenResponse;
import com.moneyflowbackend.auth.service.AuthService;
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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DebtControllerIntegrationTests {

    @Autowired AuthService authService;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired MockMvc mockMvc;

    @Test
    void createDebtRecordPaymentsSummariesAndRejectOverpayment() throws Exception {
        TokenResponse token = registerAndLogin("debt_owner");
        UUID workspaceId = workspaceRepository.findAllByUserId(token.getUser().getId()).get(0).getId();

        String firstDebt = mockMvc.perform(post("/api/workspaces/{workspaceId}/debts", workspaceId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "personName": "  Alice   Nguyen  ",
                                  "type": "RECEIVABLE",
                                  "principalAmount": 1000,
                                  "openedDate": "2026-01-01",
                                  "dueDate": "2026-02-01",
                                  "note": "Lunch"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.counterpartyName").value("Alice Nguyen"))
                .andExpect(jsonPath("$.data.direction").value("RECEIVABLE"))
                .andExpect(jsonPath("$.data.remainingAmount").value(1000))
                .andReturn().getResponse().getContentAsString();
        String debtId = JsonPath.read(firstDebt, "$.data.id");

        mockMvc.perform(post("/api/workspaces/{workspaceId}/debts/{debtId}/payments", workspaceId, debtId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentDate": "2026-01-10",
                                  "amount": 200,
                                  "note": "Partial"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(200));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/debts/{debtId}/payments", workspaceId, debtId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentDate": "2026-01-11",
                                  "amount": 801
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_EXCEEDS_REMAINING"));

        String secondDebt = mockMvc.perform(post("/api/workspaces/{workspaceId}/debts", workspaceId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "personName": "alice nguyen",
                                  "type": "PAYABLE",
                                  "principalAmount": 300,
                                  "openedDate": "2026-01-05"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secondDebtId = JsonPath.read(secondDebt, "$.data.id");

        mockMvc.perform(post("/api/workspaces/{workspaceId}/debts/{debtId}/payments", workspaceId, secondDebtId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentDate": "2026-01-12",
                                  "amount": 300
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/workspaces/{workspaceId}/debts/{debtId}/payments", workspaceId, debtId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].amount").value(200));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/debts", workspaceId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + debtId + "')].paidAmount").value(200))
                .andExpect(jsonPath("$.data[?(@.id == '" + secondDebtId + "')].status").value("PAID"));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/debts/summary", workspaceId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalReceivableRemaining").value(800))
                .andExpect(jsonPath("$.data.totalPayableRemaining").value(0))
                .andExpect(jsonPath("$.data.totalOpenDebts").value(1))
                .andExpect(jsonPath("$.data.totalPayments").value(2));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/debts/by-person", workspaceId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].personName").value("Alice Nguyen"))
                .andExpect(jsonPath("$.data[0].totalOriginalAmount").value(1300))
                .andExpect(jsonPath("$.data[0].totalPaid").value(500))
                .andExpect(jsonPath("$.data[0].totalRemaining").value(800))
                .andExpect(jsonPath("$.data[0].openDebtCount").value(1))
                .andExpect(jsonPath("$.data[0].paidDebtCount").value(1))
                .andExpect(jsonPath("$.data[0].latestOpenedDate").value("2026-01-05"))
                .andExpect(jsonPath("$.data[0].latestPaymentDate").value("2026-01-12"));
    }

    @Test
    void crossWorkspaceAccessDenied() throws Exception {
        TokenResponse owner = registerAndLogin("debt_owner_cross");
        TokenResponse outsider = registerAndLogin("debt_outsider_cross");
        Workspace ownerWorkspace = workspaceRepository.findAllByUserId(owner.getUser().getId()).get(0);

        mockMvc.perform(get("/api/workspaces/{workspaceId}/debts", ownerWorkspace.getId())
                        .header("Authorization", bearer(outsider)))
                .andExpect(status().isForbidden());
    }

    private TokenResponse registerAndLogin(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        RegisterRequest req = new RegisterRequest();
        req.setUsername(prefix + "_" + suffix);
        req.setEmail(prefix + "_" + suffix + "@example.com");
        req.setPassword("StrongPassword123");
        req.setFullName("Debt Test User");
        authService.register(req);
        LoginRequest login = new LoginRequest();
        login.setIdentifier(req.getUsername());
        login.setPassword("StrongPassword123");
        return authService.login(login);
    }

    private String bearer(TokenResponse token) {
        return "Bearer " + token.getAccessToken();
    }
}
