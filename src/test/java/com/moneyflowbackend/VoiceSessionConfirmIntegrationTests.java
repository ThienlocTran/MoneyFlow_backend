package com.moneyflowbackend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneyflowbackend.auth.dto.LoginRequest;
import com.moneyflowbackend.auth.dto.RegisterRequest;
import com.moneyflowbackend.auth.dto.TokenResponse;
import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.auth.service.AuthService;
import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryKeyword;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.category.repository.CategoryKeywordRepository;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.income.model.IncomeSource;
import com.moneyflowbackend.income.model.IncomeSourceStatus;
import com.moneyflowbackend.income.repository.IncomeSourceRepository;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.voice.session.VoiceSessionDraftRepository;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.model.WalletType;
import com.moneyflowbackend.wallet.repository.WalletRepository;
import com.moneyflowbackend.wallet.service.WalletBalanceService;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class VoiceSessionConfirmIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired CategoryKeywordRepository categoryKeywordRepository;
    @Autowired IncomeSourceRepository incomeSourceRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired VoiceSessionDraftRepository voiceSessionDraftRepository;
    @Autowired WalletBalanceService walletBalanceService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void confirmExpenseCreatesOneTraceableTransactionAndReplayDoesNotDuplicate() throws Exception {
        TestUser owner = registerAndLogin("vsc_single");
        Wallet cash = wallet(owner.workspace(), "Cash");
        Category food = category(owner.workspace(), "Ăn uống", CategoryType.EXPENSE);
        keyword(owner.workspace(), food, "ăn");
        String sessionId = interpretedSession(owner, "Tôi ăn sáng 50000");
        String draftId = firstDraftId(sessionId);
        BigDecimal before = walletBalanceService.calculateCurrentBalance(cash.getId());

        assertThat(transactionRepository.count()).isZero();
        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}/drafts/{draftId}/confirm",
                        owner.workspace().getId(), sessionId, draftId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("walletId", cash.getId(), "categoryId", food.getId(), "note", "Ăn sáng"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draftStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.confirmedEntityType").value("TRANSACTION"))
                .andExpect(jsonPath("$.data.idempotentReplay").value(false))
                .andExpect(jsonPath("$.data.transaction.voiceSessionId").value(sessionId))
                .andExpect(jsonPath("$.data.transaction.voiceSessionDraftId").value(draftId));

        assertThat(transactionRepository.count()).isEqualTo(1);
        assertThat(walletBalanceService.calculateCurrentBalance(cash.getId())).isEqualByComparingTo(before.subtract(new BigDecimal("50000.00")));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}/drafts/{draftId}/confirm",
                        owner.workspace().getId(), sessionId, draftId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("walletId", cash.getId(), "categoryId", food.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draftStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.idempotentReplay").value(true))
                .andExpect(jsonPath("$.data.warnings[0].code").value("VOICE_DRAFT_ALREADY_CONFIRMED"));

        assertThat(transactionRepository.count()).isEqualTo(1);
        assertThat(walletBalanceService.calculateCurrentBalance(cash.getId())).isEqualByComparingTo(before.subtract(new BigDecimal("50000.00")));
    }

    @Test
    void confirmAudioSessionCarriesVoiceRecordIdForPlaybackMetadata() throws Exception {
        TestUser owner = registerAndLogin("vsc_audio_record");
        Wallet cash = wallet(owner.workspace(), "Cash");
        Category food = category(owner.workspace(), "Ăn uống", CategoryType.EXPENSE);
        keyword(owner.workspace(), food, "ăn");
        String sessionId = interpretedAudioSession(owner, "Tôi ăn sáng 50000");
        String draftId = firstDraftId(sessionId);

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}/drafts/{draftId}/confirm",
                        owner.workspace().getId(), sessionId, draftId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("walletId", cash.getId(), "categoryId", food.getId(), "note", "Ăn sáng"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transaction.voiceSessionId").value(sessionId))
                .andExpect(jsonPath("$.data.transaction.voiceRecordId").isNotEmpty())
                .andExpect(jsonPath("$.data.transaction.audioStatus").value("PARSED"));
    }

    @Test
    void confirmIncomeSessionCanSaveWithoutWalletAndWithIncomeSource() throws Exception {
        TestUser owner = registerAndLogin("vsc_income");
        IncomeSource source = incomeSource(owner.workspace(), "Thu nhập của anh");
        String sessionId = interpretedSession(owner, "Hôm nay kiếm được 800000");
        String draftId = firstDraftId(sessionId);

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}/drafts/{draftId}/confirm",
                        owner.workspace().getId(), sessionId, draftId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("incomeSourceId", source.getId(), "note", "Thu nhập"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draftStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.transaction.walletId").doesNotExist())
                .andExpect(jsonPath("$.data.transaction.incomeSourceId").value(source.getId().toString()))
                .andExpect(jsonPath("$.data.transaction.affectsWalletBalance").value(false));
    }

    @Test
    void confirmIncomeSessionWithoutWalletAndWithoutIncomeSource() throws Exception {
        TestUser owner = registerAndLogin("vsc_income_no_wallet_no_source");
        String sessionId = interpretedSession(owner, "Hôm nay kiếm được 800000");
        String draftId = firstDraftId(sessionId);

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}/drafts/{draftId}/confirm",
                        owner.workspace().getId(), sessionId, draftId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draftStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.transaction.walletId").doesNotExist())
                .andExpect(jsonPath("$.data.transaction.incomeSourceId").doesNotExist())
                .andExpect(jsonPath("$.data.transaction.affectsWalletBalance").value(false));
    }

    @Test
    void missingRequiredFieldsReturnWarningsWithoutTransaction() throws Exception {
        TestUser owner = registerAndLogin("vsc_missing");
        Wallet cash = wallet(owner.workspace(), "Cash");
        String sessionId = interpretedSession(owner, "Tôi ăn sáng 50000");
        String draftId = firstDraftId(sessionId);
        var draft = voiceSessionDraftRepository.findById(UUID.fromString(draftId)).orElseThrow();
        draft.setWalletId(null);
        draft.setCategoryId(null);
        voiceSessionDraftRepository.saveAndFlush(draft);

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}/drafts/{draftId}/confirm",
                        owner.workspace().getId(), sessionId, draftId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draftStatus").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.data.warnings[0].code").value("VOICE_DRAFT_WALLET_REQUIRED"));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}/drafts/{draftId}/confirm",
                        owner.workspace().getId(), sessionId, draftId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("walletId", cash.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draftStatus").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.data.warnings[0].code").value("VOICE_DRAFT_CATEGORY_REQUIRED"));

        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void unsupportedDraftsAndBatchDoNotSilentlyConvertTypes() throws Exception {
        TestUser owner = registerAndLogin("vsc_multi");
        Wallet cash = wallet(owner.workspace(), "Cash");
        Category fuel = category(owner.workspace(), "Xăng xe", CategoryType.EXPENSE);
        keyword(owner.workspace(), fuel, "xăng");
        String sessionId = interpretedSession(owner, "Hôm nay đã kiếm được 800 Tôi ăn hết 50 Cái đổ xăng hết 65.000 Ta gửi tiết kiệm hết 35");
        var drafts = voiceSessionDraftRepository.findAllByVoiceSessionIdOrderByDraftIndexAsc(UUID.fromString(sessionId));
        assertThat(drafts).extracting("type").containsExactly("INCOME", "EXPENSE", "EXPENSE", "SAVINGS_ALLOCATION");

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}/drafts/{draftId}/confirm",
                        owner.workspace().getId(), sessionId, drafts.get(3).getId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draftStatus").value("UNSUPPORTED"))
                .andExpect(jsonPath("$.data.warnings[0].code").value("VOICE_DRAFT_CONFIRM_UNSUPPORTED"));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}/drafts/{draftId}/confirm",
                        owner.workspace().getId(), sessionId, drafts.get(2).getId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("walletId", cash.getId(), "categoryId", fuel.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draftStatus").value("CONFIRMED"));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}/confirm-eligible",
                        owner.workspace().getId(), sessionId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confirmedCount").value(1))
                .andExpect(jsonPath("$.data.sessionStatus").value("PARTIALLY_CONFIRMED"));

        assertThat(transactionRepository.count()).isEqualTo(2);
    }

    @Test
    void reinterpretAfterConfirmAndCrossWorkspaceConfirmAreRejected() throws Exception {
        TestUser owner = registerAndLogin("vsc_guard");
        TestUser other = registerAndLogin("vsc_guard_other");
        Wallet cash = wallet(owner.workspace(), "Cash");
        Category food = category(owner.workspace(), "Ăn uống", CategoryType.EXPENSE);
        keyword(owner.workspace(), food, "ăn");
        String sessionId = interpretedSession(owner, "Tôi ăn sáng 50000");
        String draftId = firstDraftId(sessionId);

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}/drafts/{draftId}/confirm",
                        other.workspace().getId(), sessionId, draftId)
                        .header("Authorization", bearer(other.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("walletId", cash.getId(), "categoryId", food.getId()))))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}/drafts/{draftId}/confirm",
                        owner.workspace().getId(), sessionId, draftId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("walletId", cash.getId(), "categoryId", food.getId()))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}/interpret", owner.workspace().getId(), sessionId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VOICE_SESSION_ALREADY_HAS_CONFIRMED_DRAFTS"));
    }

    private String interpretedSession(TestUser owner, String transcript) throws Exception {
        String sessionId = createSession(owner);
        mockMvc.perform(patch("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}/transcript", owner.workspace().getId(), sessionId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("transcript", transcript))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}/interpret", owner.workspace().getId(), sessionId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        return sessionId;
    }

    private String interpretedAudioSession(TestUser owner, String transcript) throws Exception {
        String sessionId = createSession(owner, "AUDIO");
        mockMvc.perform(patch("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}/transcript", owner.workspace().getId(), sessionId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("transcript", transcript))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}/interpret", owner.workspace().getId(), sessionId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        return sessionId;
    }

    private String createSession(TestUser owner) throws Exception {
        return createSession(owner, "TEXT");
    }

    private String createSession(TestUser owner, String sourceType) throws Exception {
        String body = mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-sessions", owner.workspace().getId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sourceType", sourceType))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("sessionId").asText();
    }

    private String firstDraftId(String sessionId) {
        return voiceSessionDraftRepository.findAllByVoiceSessionIdOrderByDraftIndexAsc(UUID.fromString(sessionId)).getFirst().getId().toString();
    }

    private TestUser registerAndLogin(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        RegisterRequest request = new RegisterRequest();
        request.setUsername(prefix + "_" + suffix);
        request.setEmail(prefix + "_" + suffix + "@example.com");
        request.setPassword("StrongPassword123");
        request.setFullName("Voice Session Confirm Test User");
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
                .name(name)
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

    private void keyword(Workspace workspace, Category category, String value) {
        categoryKeywordRepository.saveAndFlush(CategoryKeyword.builder()
                .workspace(workspace)
                .category(category)
                .keyword(value)
                .priority(10)
                .isUserLearned(true)
                .build());
    }

    private IncomeSource incomeSource(Workspace workspace, String name) {
        return incomeSourceRepository.saveAndFlush(IncomeSource.builder()
                .workspace(workspace)
                .name(name)
                .status(IncomeSourceStatus.ACTIVE)
                .createdByUser(workspace.getCreatedByUser())
                .build());
    }

    private String bearer(TokenResponse token) {
        return "Bearer " + token.getAccessToken();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private record TestUser(User user, Workspace workspace, TokenResponse token) {
    }
}
