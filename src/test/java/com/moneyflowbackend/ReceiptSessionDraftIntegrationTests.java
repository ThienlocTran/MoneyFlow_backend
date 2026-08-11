package com.moneyflowbackend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneyflowbackend.auth.dto.LoginRequest;
import com.moneyflowbackend.auth.dto.RegisterRequest;
import com.moneyflowbackend.auth.dto.TokenResponse;
import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.auth.service.AuthService;
import com.moneyflowbackend.receipt.session.ReceiptSession;
import com.moneyflowbackend.receipt.session.ReceiptSessionDraftRepository;
import com.moneyflowbackend.receipt.session.ReceiptSessionOcrStatus;
import com.moneyflowbackend.receipt.session.ReceiptSessionRepository;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "MONEYFLOW_RECEIPT_OCR_PROVIDER=mock")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReceiptSessionDraftIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired ReceiptSessionRepository receiptSessionRepository;
    @Autowired ReceiptSessionDraftRepository receiptSessionDraftRepository;
    @Autowired TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void buildDraftFromStructuredOcrWithoutTransaction() throws Exception {
        TestUser owner = registerAndLogin("receipt_draft_structured");
        UUID sessionId = createSession(owner);
        upload(owner, sessionId, "coffee.jpg");
        runOcr(owner, sessionId);
        long txBefore = transactionRepository.count();

        mockMvc.perform(post("/api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/drafts", owner.workspace().getId(), sessionId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.data.drafts.length()").value(1))
                .andExpect(jsonPath("$.data.drafts[0].type").value("EXPENSE"))
                .andExpect(jsonPath("$.data.drafts[0].amount").value(25000))
                .andExpect(jsonPath("$.data.drafts[0].merchantName").value("QUAN CA PHE DEMO"))
                .andExpect(jsonPath("$.data.drafts[0].transactionDate").value("2026-08-01"))
                .andExpect(jsonPath("$.data.drafts[0].walletId").doesNotExist())
                .andExpect(jsonPath("$.data.drafts[0].categoryId").doesNotExist())
                .andExpect(jsonPath("$.data.drafts[0].categoryHint").value("Cà phê"))
                .andExpect(jsonPath("$.data.drafts[0].warnings[*].code",
                        containsInAnyOrder("RECEIPT_DRAFT_MISSING_WALLET", "RECEIPT_CATEGORY_HINT_ONLY")))
                .andExpect(jsonPath("$.data.nextActions[0]").value("REVIEW_DRAFT"));

        assertThat(transactionRepository.count()).isEqualTo(txBefore);
    }

    @Test
    void buildDraftFromRawTextTotalLineAndMultipleAmounts() throws Exception {
        TestUser owner = registerAndLogin("receipt_draft_raw");
        UUID sessionId = createSession(owner);
        setOcr(owner, sessionId, """
                HOA DON
                Ca phe 25.000
                Banh mi 20.000
                Tong cong 45.000
                """, null, null, null);

        mockMvc.perform(post("/api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/drafts", owner.workspace().getId(), sessionId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.drafts[0].amount").value(45000))
                .andExpect(jsonPath("$.data.drafts[0].categoryHint").value("Cà phê"));
    }

    @Test
    void buildDraftInfersLargestAmountWhenNoTotalMarker() throws Exception {
        TestUser owner = registerAndLogin("receipt_draft_infer");
        UUID sessionId = createSession(owner);
        setOcr(owner, sessionId, """
                HOA DON
                Mon A 25.000
                Mon B 35.000
                """, null, null, null);

        mockMvc.perform(post("/api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/drafts", owner.workspace().getId(), sessionId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.drafts[0].amount").value(35000))
                .andExpect(jsonPath("$.data.drafts[0].warnings[?(@.code == 'RECEIPT_TOTAL_INFERRED')]").exists());
    }

    @Test
    void dateParseAndIdempotentRebuildAvoidDuplicateDrafts() throws Exception {
        TestUser owner = registerAndLogin("receipt_draft_date");
        UUID sessionId = createSession(owner);
        setOcr(owner, sessionId, """
                MINI MART
                Ngay 02/08/2026
                Tong cong 35.000
                """, null, null, null);

        buildDrafts(owner, sessionId);
        buildDrafts(owner, sessionId);

        mockMvc.perform(post("/api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/drafts", owner.workspace().getId(), sessionId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.drafts.length()").value(1))
                .andExpect(jsonPath("$.data.drafts[0].transactionDate").value("2026-08-02"));
        assertThat(receiptSessionDraftRepository.findAllByReceiptSessionIdOrderByDraftIndexAsc(sessionId)).hasSize(1);
    }

    @Test
    void missingOcrReturnsWarningWithoutDraftOrTransaction() throws Exception {
        TestUser owner = registerAndLogin("receipt_draft_missing_ocr");
        UUID sessionId = createSession(owner);
        long txBefore = transactionRepository.count();

        mockMvc.perform(post("/api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/drafts", owner.workspace().getId(), sessionId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.drafts.length()").value(0))
                .andExpect(jsonPath("$.data.warnings[0].code").value("RECEIPT_OCR_REQUIRED"));

        assertThat(transactionRepository.count()).isEqualTo(txBefore);
    }

    @Test
    void workspaceIsolationBlocksDraftBuild() throws Exception {
        TestUser owner = registerAndLogin("receipt_draft_owner");
        TestUser other = registerAndLogin("receipt_draft_other");
        UUID sessionId = createSession(owner);
        setOcr(owner, sessionId, "Tong cong 35.000", null, null, null);

        mockMvc.perform(post("/api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/drafts", other.workspace().getId(), sessionId)
                        .header("Authorization", bearer(other.token())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECEIPT_SESSION_NOT_FOUND"));
    }

    private UUID createSession(TestUser user) throws Exception {
        String body = mockMvc.perform(post("/api/workspaces/{workspaceId}/receipt-sessions", user.workspace().getId())
                        .header("Authorization", bearer(user.token()))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return UUID.fromString(objectMapper.readTree(body).path("data").path("id").asText());
    }

    private void upload(TestUser user, UUID sessionId, String filename) throws Exception {
        mockMvc.perform(multipart("/api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/image", user.workspace().getId(), sessionId)
                        .file(new MockMultipartFile("file", filename, "image/jpeg", new byte[] {1, 2, 3}))
                        .header("Authorization", bearer(user.token())))
                .andExpect(status().isOk());
    }

    private void runOcr(TestUser user, UUID sessionId) throws Exception {
        mockMvc.perform(post("/api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/ocr", user.workspace().getId(), sessionId)
                        .header("Authorization", bearer(user.token())))
                .andExpect(status().isOk());
    }

    private void buildDrafts(TestUser user, UUID sessionId) throws Exception {
        mockMvc.perform(post("/api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/drafts", user.workspace().getId(), sessionId)
                        .header("Authorization", bearer(user.token())))
                .andExpect(status().isOk());
    }

    private void setOcr(TestUser user, UUID sessionId, String text, String merchant, LocalDate date, BigDecimal total) {
        ReceiptSession session = receiptSessionRepository.findByIdAndWorkspaceIdAndDeletedAtIsNull(sessionId, user.workspace().getId()).orElseThrow();
        session.setOcrStatus(ReceiptSessionOcrStatus.SUCCEEDED);
        session.setOcrProvider("TEST");
        session.setRawOcrText(text);
        session.setNormalizedOcrText(text);
        session.setMerchantName(merchant);
        session.setReceiptDate(date);
        session.setTotalAmount(total);
        receiptSessionRepository.save(session);
    }

    private TestUser registerAndLogin(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        RegisterRequest request = new RegisterRequest();
        request.setUsername(prefix + "_" + suffix);
        request.setEmail(prefix + "_" + suffix + "@example.com");
        request.setPassword("StrongPassword123");
        request.setFullName("Receipt Draft Test User");
        authService.register(request);
        LoginRequest login = new LoginRequest();
        login.setIdentifier(request.getUsername());
        login.setPassword("StrongPassword123");
        TokenResponse token = authService.login(login);
        Workspace workspace = workspaceRepository.findAllByUserId(token.getUser().getId()).getFirst();
        User user = userRepository.getReferenceById(token.getUser().getId());
        return new TestUser(user, workspace, token);
    }

    private String bearer(TokenResponse token) {
        return "Bearer " + token.getAccessToken();
    }

    private record TestUser(User user, Workspace workspace, TokenResponse token) {
    }
}
