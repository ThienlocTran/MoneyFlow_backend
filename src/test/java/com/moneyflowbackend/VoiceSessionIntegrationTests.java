package com.moneyflowbackend;

import com.fasterxml.jackson.databind.JsonNode;
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
import com.moneyflowbackend.emergencyfund.repository.EmergencyFundLedgerEntryRepository;
import com.moneyflowbackend.savingsgoal.repository.SavingsGoalLedgerEntryRepository;
import com.moneyflowbackend.sinkingfund.repository.SinkingFundAllocationRepository;
import com.moneyflowbackend.transaction.model.Transaction;
import com.moneyflowbackend.transaction.model.TransactionSourceType;
import com.moneyflowbackend.transaction.model.TransactionStatus;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.voice.repository.VoiceRecordRepository;
import com.moneyflowbackend.voice.session.VoiceSessionDraftRepository;
import com.moneyflowbackend.voice.session.VoiceSessionRepository;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.model.WalletType;
import com.moneyflowbackend.wallet.repository.WalletBalanceSnapshotRepository;
import com.moneyflowbackend.wallet.repository.WalletRepository;
import com.moneyflowbackend.wallet.service.WalletBalanceService;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import jakarta.persistence.EntityManager;
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
class VoiceSessionIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired CategoryKeywordRepository categoryKeywordRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired VoiceRecordRepository voiceRecordRepository;
    @Autowired VoiceSessionRepository voiceSessionRepository;
    @Autowired VoiceSessionDraftRepository voiceSessionDraftRepository;
    @Autowired WalletBalanceService walletBalanceService;
    @Autowired SavingsGoalLedgerEntryRepository savingsGoalLedgerEntryRepository;
    @Autowired EmergencyFundLedgerEntryRepository emergencyFundLedgerEntryRepository;
    @Autowired SinkingFundAllocationRepository sinkingFundAllocationRepository;
    @Autowired WalletBalanceSnapshotRepository walletBalanceSnapshotRepository;
    @Autowired EntityManager entityManager;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void createTextAndAudioSessionsStartWithNoAsr() throws Exception {
        TestUser owner = registerAndLogin("voice_session_create");

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-sessions", owner.workspace().getId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sourceType", "TEXT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sourceType").value("TEXT"))
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.audioStatus").value("NONE"))
                .andExpect(jsonPath("$.data.asrStatus").value("NOT_REQUESTED"))
                .andExpect(jsonPath("$.data.commandStatus").value("NOT_REQUESTED"))
                .andExpect(jsonPath("$.data.confirmStatus").value("NOT_CONFIRMED"));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-sessions", owner.workspace().getId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sourceType", "AUDIO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sourceType").value("AUDIO"))
                .andExpect(jsonPath("$.data.audioStatus").value("NONE"))
                .andExpect(jsonPath("$.data.asrStatus").value("NOT_REQUESTED"));
    }

    @Test
    void createRejectsInvalidSourceTypeAndUnauthorizedWorkspace() throws Exception {
        TestUser owner = registerAndLogin("voice_session_bad_source");

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-sessions", owner.workspace().getId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceType\":\"BAD\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-sessions", owner.workspace().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sourceType", "TEXT"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateTranscriptStoresNfcAndRejectsBlankOrCrossWorkspace() throws Exception {
        TestUser owner = registerAndLogin("voice_session_transcript");
        TestUser other = registerAndLogin("voice_session_transcript_other");
        String sessionId = createSession(owner, "TEXT");
        String decomposed = "a\u0306n sa\u0301ng 35k";

        mockMvc.perform(patch("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}/transcript", owner.workspace().getId(), sessionId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("transcript", decomposed))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transcript").value("ăn sáng 35k"))
                .andExpect(jsonPath("$.data.normalizedTranscript").value("ăn sáng 35k"))
                .andExpect(jsonPath("$.data.status").value("CREATED"));

        mockMvc.perform(patch("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}/transcript", owner.workspace().getId(), sessionId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("transcript", "   "))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}", other.workspace().getId(), sessionId)
                        .header("Authorization", bearer(other.token())))
                .andExpect(status().isNotFound());
    }

    @Test
    void interpretRejectsMissingTranscript() throws Exception {
        TestUser owner = registerAndLogin("vs_missing");
        String sessionId = createSession(owner, "TEXT");

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}/interpret", owner.workspace().getId(), sessionId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VOICE_SESSION_TRANSCRIPT_REQUIRED"));
    }

    @Test
    void interpretSingleExpensePersistsOneDraftWithoutPosting() throws Exception {
        TestUser owner = registerAndLogin("voice_session_single");
        Wallet cash = wallet(owner.workspace(), "Cash");
        Category food = category(owner.workspace(), "Ăn uống", CategoryType.EXPENSE);
        keyword(owner.workspace(), food, "ăn");
        String sessionId = createSession(owner, "TEXT");
        updateTranscript(owner, sessionId, "hôm nay tôi ăn hết 50k");
        long txBefore = transactionRepository.count();
        long voiceBefore = voiceRecordRepository.count();
        BigDecimal balanceBefore = walletBalanceService.calculateCurrentBalance(cash.getId());

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}/interpret", owner.workspace().getId(), sessionId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.data.asrStatus").value("NOT_REQUESTED"))
                .andExpect(jsonPath("$.data.commandStatus").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.data.mode").value("TRANSACTION_REVIEW"))
                .andExpect(jsonPath("$.data.drafts.length()").value(1))
                .andExpect(jsonPath("$.data.drafts[0].type").value("EXPENSE"))
                .andExpect(jsonPath("$.data.drafts[0].amount").value(50000))
                .andExpect(jsonPath("$.data.drafts[0].categoryId").value(food.getId().toString()));

        assertThat(transactionRepository.count()).isEqualTo(txBefore);
        assertThat(voiceRecordRepository.count()).isEqualTo(voiceBefore);
        assertThat(walletBalanceService.calculateCurrentBalance(cash.getId())).isEqualByComparingTo(balanceBefore);
        assertThat(voiceSessionDraftRepository.findAllByVoiceSessionIdOrderByDraftIndexAsc(UUID.fromString(sessionId))).hasSize(1);
    }

    @Test
    void interpretRequiredMultiIntentStoresFourDraftsAndWarningsWithoutPosting() throws Exception {
        TestUser owner = registerAndLogin("voice_session_multi");
        Wallet cash = wallet(owner.workspace(), "Cash");
        Category fuel = category(owner.workspace(), "Xăng xe", CategoryType.EXPENSE);
        keyword(owner.workspace(), fuel, "xăng");
        String sessionId = createSession(owner, "TEXT");
        updateTranscript(owner, sessionId, "Hôm nay đã kiếm được 800 Tôi ăn hết 50 Cái đổ xăng hết 65.000 Ta gửi tiết kiệm hết 35");
        Counts before = counts(cash);

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}/interpret", owner.workspace().getId(), sessionId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("MULTI_DRAFT_REVIEW"))
                .andExpect(jsonPath("$.data.drafts.length()").value(4))
                .andExpect(jsonPath("$.data.drafts[0].draftIndex").value(0))
                .andExpect(jsonPath("$.data.drafts[0].type").value("INCOME_FACT"))
                .andExpect(jsonPath("$.data.drafts[0].walletRequired").value(false))
                .andExpect(jsonPath("$.data.drafts[0].affectsWalletBalance").value(false))
                .andExpect(jsonPath("$.data.drafts[1].type").value("EXPENSE"))
                .andExpect(jsonPath("$.data.drafts[2].type").value("EXPENSE"))
                .andExpect(jsonPath("$.data.drafts[2].categoryId").value(fuel.getId().toString()))
                .andExpect(jsonPath("$.data.drafts[3].type").value("SAVINGS_ALLOCATION"))
                .andExpect(jsonPath("$.data.drafts[3].categoryRequired").value(false))
                .andExpect(jsonPath("$.data.drafts[3].status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.data.commandWarnings[1].code").value("VOICE_MULTI_INTENT_DETECTED"));

        assertSafety(cash, before);
        assertThat(voiceSessionDraftRepository.findAllByVoiceSessionIdOrderByDraftIndexAsc(UUID.fromString(sessionId)))
                .extracting("type")
                .containsExactly("INCOME_FACT", "EXPENSE", "EXPENSE", "SAVINGS_ALLOCATION");
    }

    @Test
    void reinterpretReplacesPreviousDrafts() throws Exception {
        TestUser owner = registerAndLogin("voice_session_reinterpret");
        wallet(owner.workspace(), "Cash");
        Category food = category(owner.workspace(), "Ăn uống", CategoryType.EXPENSE);
        keyword(owner.workspace(), food, "ăn");
        String sessionId = createSession(owner, "TEXT");
        updateTranscript(owner, sessionId, "ăn 50k");
        interpret(owner, sessionId);
        assertThat(voiceSessionDraftRepository.findAllByVoiceSessionIdOrderByDraftIndexAsc(UUID.fromString(sessionId))).hasSize(1);

        updateTranscript(owner, sessionId, "ăn 50k ăn 60k");
        interpret(owner, sessionId);

        assertThat(voiceSessionDraftRepository.findAllByVoiceSessionIdOrderByDraftIndexAsc(UUID.fromString(sessionId))).hasSize(2);
    }

    @Test
    void getDetailReturnsSessionWithDraftsAndDoesNotLeakAcrossWorkspaces() throws Exception {
        TestUser owner = registerAndLogin("voice_session_detail");
        TestUser other = registerAndLogin("voice_session_detail_other");
        wallet(owner.workspace(), "Cash");
        Category food = category(owner.workspace(), "Ăn uống", CategoryType.EXPENSE);
        keyword(owner.workspace(), food, "ăn");
        String sessionId = createSession(owner, "TEXT");
        updateTranscript(owner, sessionId, "ăn 50k");
        interpret(owner, sessionId);

        mockMvc.perform(get("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}", owner.workspace().getId(), sessionId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.audioStatus").value("NONE"))
                .andExpect(jsonPath("$.data.asrStatus").value("NOT_REQUESTED"))
                .andExpect(jsonPath("$.data.commandStatus").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.data.confirmStatus").value("NOT_CONFIRMED"))
                .andExpect(jsonPath("$.data.drafts.length()").value(1));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}", other.workspace().getId(), sessionId)
                        .header("Authorization", bearer(other.token())))
                .andExpect(status().isNotFound());
    }

    @Test
    void legacyVoiceCommandStillWorksWithoutCreatingSession() throws Exception {
        TestUser owner = registerAndLogin("voice_session_legacy");
        wallet(owner.workspace(), "Cash");
        Category food = category(owner.workspace(), "Ăn uống", CategoryType.EXPENSE);
        keyword(owner.workspace(), food, "ăn");
        long sessionsBefore = voiceSessionRepository.count();

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-command/interpret", owner.workspace().getId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("text", "hôm nay tôi ăn hết 50k"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("TRANSACTION_REVIEW"))
                .andExpect(jsonPath("$.data.review.candidate.type").value("EXPENSE"));

        assertThat(voiceSessionRepository.count()).isEqualTo(sessionsBefore);
    }

    private String createSession(TestUser owner, String sourceType) throws Exception {
        String body = mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-sessions", owner.workspace().getId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sourceType", sourceType))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(body);
        return node.path("data").path("sessionId").asText();
    }

    private void updateTranscript(TestUser owner, String sessionId, String transcript) throws Exception {
        mockMvc.perform(patch("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}/transcript", owner.workspace().getId(), sessionId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("transcript", transcript))))
                .andExpect(status().isOk());
    }

    private void interpret(TestUser owner, String sessionId) throws Exception {
        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}/interpret", owner.workspace().getId(), sessionId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    private Counts counts(Wallet wallet) {
        return new Counts(
                transactionRepository.count(),
                debtPaymentCount(),
                savingsGoalLedgerEntryRepository.count(),
                emergencyFundLedgerEntryRepository.count(),
                sinkingFundAllocationRepository.count(),
                walletBalanceSnapshotRepository.count(),
                walletBalanceService.calculateCurrentBalance(wallet.getId()));
    }

    private void assertSafety(Wallet wallet, Counts before) {
        assertThat(transactionRepository.count()).isEqualTo(before.transactions());
        assertThat(debtPaymentCount()).isEqualTo(before.debtPayments());
        assertThat(savingsGoalLedgerEntryRepository.count()).isEqualTo(before.savingsLedger());
        assertThat(emergencyFundLedgerEntryRepository.count()).isEqualTo(before.emergencyLedger());
        assertThat(sinkingFundAllocationRepository.count()).isEqualTo(before.sinkingLedger());
        assertThat(walletBalanceSnapshotRepository.count()).isEqualTo(before.walletSnapshots());
        assertThat(walletBalanceService.calculateCurrentBalance(wallet.getId())).isEqualByComparingTo(before.walletBalance());
    }

    private TestUser registerAndLogin(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        RegisterRequest request = new RegisterRequest();
        request.setUsername(prefix + "_" + suffix);
        request.setEmail(prefix + "_" + suffix + "@example.com");
        request.setPassword("StrongPassword123");
        request.setFullName("Voice Session Test User");
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

    private Transaction transaction(Workspace workspace, User user, Wallet wallet, Category category, String amount, LocalDate date) {
        return transactionRepository.saveAndFlush(Transaction.builder()
                .workspace(workspace)
                .createdByUser(user)
                .wallet(wallet)
                .category(category)
                .transactionType(TransactionType.EXPENSE)
                .transactionStatus(TransactionStatus.POSTED)
                .amount(new BigDecimal(amount))
                .currency("VND")
                .transactionDate(date)
                .description(category.getName())
                .sourceType(TransactionSourceType.MANUAL)
                .affectsWalletBalance(true)
                .build());
    }

    private long debtPaymentCount() {
        return entityManager.createQuery("select count(payment) from DebtPayment payment", Long.class)
                .getSingleResult();
    }

    private String bearer(TokenResponse token) {
        return "Bearer " + token.getAccessToken();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private record TestUser(User user, Workspace workspace, TokenResponse token) {
    }

    private record Counts(long transactions, long debtPayments, long savingsLedger, long emergencyLedger,
                          long sinkingLedger, long walletSnapshots, BigDecimal walletBalance) {
    }
}
