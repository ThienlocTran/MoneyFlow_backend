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
import com.moneyflowbackend.receipt.session.ReceiptImageStorageStatus;
import com.moneyflowbackend.receipt.session.ReceiptSession;
import com.moneyflowbackend.receipt.session.ReceiptSessionDraft;
import com.moneyflowbackend.receipt.session.ReceiptSessionDraftRepository;
import com.moneyflowbackend.receipt.session.ReceiptSessionDraftStatus;
import com.moneyflowbackend.receipt.session.ReceiptSessionOcrStatus;
import com.moneyflowbackend.receipt.session.ReceiptSessionRepository;
import com.moneyflowbackend.receipt.session.ReceiptSessionStatus;
import com.moneyflowbackend.transaction.model.TransactionSourceType;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.model.WalletType;
import com.moneyflowbackend.wallet.repository.WalletRepository;
import com.moneyflowbackend.wallet.service.WalletService;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "MONEYFLOW_RECEIPT_OCR_PROVIDER=mock")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReceiptSessionConfirmIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ReceiptSessionRepository receiptSessionRepository;
    @Autowired ReceiptSessionDraftRepository receiptSessionDraftRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired WalletService walletService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void confirmValidDraftCreatesReceiptTransactionAndUpdatesBalance() throws Exception {
        TestUser owner = registerAndLogin("receipt_confirm_valid");
        Wallet cash = wallet(owner, "Cash", "100000");
        Category food = category(owner, "Food");
        ReceiptSessionDraft draft = draft(session(owner), "25000", null);

        confirm(owner, draft, Map.of(
                        "walletId", cash.getId(),
                        "categoryId", food.getId(),
                        "note", "Receipt: Coffee Shop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draftStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.confirmedEntityType").value("TRANSACTION"))
                .andExpect(jsonPath("$.data.transaction.sourceType").value("RECEIPT"))
                .andExpect(jsonPath("$.data.transaction.receiptSessionId").value(draft.getReceiptSession().getId().toString()))
                .andExpect(jsonPath("$.data.transaction.receiptSessionDraftId").value(draft.getId().toString()));

        var savedDraft = receiptSessionDraftRepository.findById(draft.getId()).orElseThrow();
        assertThat(savedDraft.getConfirmedEntityId()).isNotNull();
        assertThat(walletService.calculateCurrentBalance(cash.getId())).isEqualByComparingTo("75000");
    }

    @Test
    void confirmValidationWarningsDoNotCreateTransaction() throws Exception {
        TestUser owner = registerAndLogin("receipt_confirm_warning");
        ReceiptSessionDraft missingAmount = draft(session(owner), null, null);
        long before = transactionRepository.count();

        confirm(owner, missingAmount, Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draftStatus").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.data.warnings[0].code").value("RECEIPT_DRAFT_MISSING_AMOUNT"));
        assertThat(transactionRepository.count()).isEqualTo(before);

        ReceiptSessionDraft missingWallet = draft(session(owner), "12000", null);
        confirm(owner, missingWallet, Map.of("categoryId", category(owner, "Cafe").getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.warnings[0].code").value("RECEIPT_DRAFT_MISSING_WALLET"));

        ReceiptSessionDraft missingCategory = draft(session(owner), "12000", null);
        confirm(owner, missingCategory, Map.of("walletId", wallet(owner, "Wallet", "0").getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.warnings[0].code").value("RECEIPT_DRAFT_MISSING_CATEGORY"));
    }

    @Test
    void confirmIsIdempotent() throws Exception {
        TestUser owner = registerAndLogin("receipt_confirm_idem");
        ReceiptSessionDraft draft = draft(session(owner), "19000", null);
        Wallet cash = wallet(owner, "Cash", "50000");
        Category food = category(owner, "Food");
        Map<String, Object> req = Map.of("walletId", cash.getId(), "categoryId", food.getId());

        confirm(owner, draft, req).andExpect(status().isOk());
        long count = transactionRepository.count();
        confirm(owner, draft, req)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idempotentReplay").value(true))
                .andExpect(jsonPath("$.data.warnings[0].code").value("RECEIPT_DRAFT_ALREADY_CONFIRMED"));

        assertThat(transactionRepository.count()).isEqualTo(count);
    }

    @Test
    void confirmOneOfMultipleDraftsMarksSessionPartial() throws Exception {
        TestUser owner = registerAndLogin("receipt_confirm_partial");
        ReceiptSession session = session(owner);
        ReceiptSessionDraft first = draft(session, "10000", 0);
        draft(session, "20000", 1);

        confirm(owner, first, Map.of("walletId", wallet(owner, "Cash", "50000").getId(), "categoryId", category(owner, "Food").getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session.status").value("PARTIALLY_CONFIRMED"));

        assertThat(receiptSessionRepository.findByIdAndWorkspaceIdAndDeletedAtIsNull(session.getId(), owner.workspace().getId()).orElseThrow().getStatus())
                .isEqualTo(ReceiptSessionStatus.PARTIALLY_CONFIRMED);
    }

    @Test
    void workspaceIsolationBlocksWrongSessionAndWrongReferences() throws Exception {
        TestUser owner = registerAndLogin("receipt_confirm_owner");
        TestUser other = registerAndLogin("receipt_confirm_other");
        ReceiptSessionDraft draft = draft(session(owner), "10000", null);

        confirm(other, draft, Map.of())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECEIPT_SESSION_NOT_FOUND"));

        confirm(owner, draft, Map.of("walletId", wallet(other, "Other Cash", "0").getId(), "categoryId", category(other, "Other Food").getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.warnings[0].code").value("RECEIPT_DRAFT_INVALID_STATE"));
    }

    @Test
    void uploadOcrAndDraftBuildDoNotAutoCreateTransaction() throws Exception {
        TestUser owner = registerAndLogin("receipt_confirm_no_auto");
        UUID sessionId = createSession(owner);
        long before = transactionRepository.count();

        mockMvc.perform(multipart("/api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/image", owner.workspace().getId(), sessionId)
                        .file(new MockMultipartFile("file", "receipt.jpg", "image/jpeg", new byte[] {1, 2, 3}))
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/ocr", owner.workspace().getId(), sessionId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/drafts", owner.workspace().getId(), sessionId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk());

        assertThat(transactionRepository.count()).isEqualTo(before);
    }

    private org.springframework.test.web.servlet.ResultActions confirm(TestUser user, ReceiptSessionDraft draft, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/drafts/{draftId}/confirm",
                        user.workspace().getId(), draft.getReceiptSession().getId(), draft.getId())
                        .header("Authorization", bearer(user.token()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(body)));
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

    private ReceiptSession session(TestUser user) {
        return receiptSessionRepository.saveAndFlush(ReceiptSession.builder()
                .workspace(user.workspace())
                .createdByUser(user.user())
                .status(ReceiptSessionStatus.DRAFTED)
                .imageStorageStatus(ReceiptImageStorageStatus.STORAGE_NOT_CONFIGURED)
                .ocrStatus(ReceiptSessionOcrStatus.SUCCEEDED)
                .normalizedOcrText("Coffee Shop\nTotal 25000")
                .currency("VND")
                .build());
    }

    private ReceiptSessionDraft draft(ReceiptSession session, String amount, Integer index) {
        return receiptSessionDraftRepository.saveAndFlush(ReceiptSessionDraft.builder()
                .receiptSession(session)
                .workspaceId(session.getWorkspace().getId())
                .draftIndex(index == null ? 0 : index)
                .type("EXPENSE")
                .status(ReceiptSessionDraftStatus.DRAFT)
                .amount(amount == null ? null : new BigDecimal(amount))
                .currency("VND")
                .transactionDate(LocalDate.of(2026, 8, 11))
                .merchantName("Coffee Shop")
                .note("Coffee Shop")
                .sourceText("Coffee Shop Total " + amount)
                .build());
    }

    private Wallet wallet(TestUser user, String name, String openingBalance) {
        return walletRepository.saveAndFlush(Wallet.builder()
                .workspace(user.workspace())
                .name(name)
                .walletType(WalletType.CASH)
                .openingBalance(new BigDecimal(openingBalance))
                .isActive(true)
                .includeInTotal(true)
                .build());
    }

    private Category category(TestUser user, String name) {
        return categoryRepository.saveAndFlush(Category.builder()
                .workspace(user.workspace())
                .name(name)
                .categoryType(CategoryType.EXPENSE)
                .isActive(true)
                .isArchived(false)
                .build());
    }

    private TestUser registerAndLogin(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        RegisterRequest request = new RegisterRequest();
        request.setUsername(prefix + "_" + suffix);
        request.setEmail(prefix + "_" + suffix + "@example.com");
        request.setPassword("StrongPassword123");
        request.setFullName("Receipt Confirm Test User");
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
