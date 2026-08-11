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
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.voice.asr.VoiceAsrClient;
import com.moneyflowbackend.voice.asr.VoiceAsrProviderType;
import com.moneyflowbackend.voice.asr.VoiceAsrRequest;
import com.moneyflowbackend.voice.asr.VoiceAsrTranscribeResult;
import com.moneyflowbackend.voice.asr.VoiceAsrWarning;
import com.moneyflowbackend.voice.repository.VoiceRecordRepository;
import com.moneyflowbackend.voice.session.VoiceSessionAsrStatus;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "MONEYFLOW_ASR_PROVIDER=mock",
        "MONEYFLOW_ASR_MAX_FILE_BYTES=3"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class VoiceAsrIntegrationTests {
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
    @Autowired StubVoiceAsrClient asrClient;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void resetAsr() {
        asrClient.result = success("Hôm nay tôi ăn sáng hết 35 nghìn");
    }

    @Test
    void transcribeValidationErrorsAreStable() throws Exception {
        TestUser owner = registerAndLogin("vasr_validation");
        String sessionId = createSession(owner);

        mockMvc.perform(multipart("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}/transcribe", owner.workspace().getId(), sessionId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ASR_AUDIO_REQUIRED"));

        mockMvc.perform(multipart("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}/transcribe", owner.workspace().getId(), sessionId)
                        .file(file("clip.txt", "text/plain", "abc"))
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("ASR_UNSUPPORTED_AUDIO_FORMAT"));

        mockMvc.perform(multipart("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}/transcribe", owner.workspace().getId(), sessionId)
                        .file(file("clip.webm", "audio/webm", "abcd"))
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("ASR_FILE_TOO_LARGE"));

        mockMvc.perform(multipart("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}/transcribe", owner.workspace().getId(), sessionId)
                        .file(file("clip.webm", "audio/webm", "abc"))
                        .param("durationMs", "100")
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ASR_AUDIO_TOO_SHORT"));
    }

    @Test
    void mockSuccessUpdatesTranscriptOnly() throws Exception {
        TestUser owner = registerAndLogin("vasr_success");
        Wallet cash = wallet(owner.workspace(), "Cash");
        String sessionId = createSession(owner);
        Counts before = counts(cash);

        mockMvc.perform(multipart("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}/transcribe", owner.workspace().getId(), sessionId)
                        .file(file("clip.webm", "audio/webm", "abc"))
                        .param("durationMs", "1000")
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("TRANSCRIBED"))
                .andExpect(jsonPath("$.data.audioStatus").value("NONE"))
                .andExpect(jsonPath("$.data.asrStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.commandStatus").value("NOT_REQUESTED"))
                .andExpect(jsonPath("$.data.transcript").value("Hôm nay tôi ăn sáng hết 35 nghìn"))
                .andExpect(jsonPath("$.data.voiceRecordId").isNotEmpty())
                .andExpect(jsonPath("$.data.asr.provider").value("MOCK"))
                .andExpect(jsonPath("$.data.asr.warnings[0].code").value("ASR_MOCK_TRANSCRIPT"));

        assertSafety(cash, before);
        assertThat(voiceRecordRepository.count()).isOne();
        assertThat(voiceSessionDraftRepository.findAllByVoiceSessionIdOrderByDraftIndexAsc(UUID.fromString(sessionId))).isEmpty();
    }

    @Test
    void transcribeFailureUpdatesSessionWithoutDraftsOrTransactions() throws Exception {
        TestUser owner = registerAndLogin("vasr_fail");
        Wallet cash = wallet(owner.workspace(), "Cash");
        String sessionId = createSession(owner);
        Counts before = counts(cash);
        asrClient.result = new VoiceAsrTranscribeResult(
                VoiceAsrProviderType.MOCK,
                VoiceSessionAsrStatus.TIMEOUT,
                "mock",
                "vi",
                null,
                null,
                null,
                null,
                List.of(new VoiceAsrWarning("ASR_SERVICE_TIMEOUT", "Dịch vụ nhận diện giọng nói phản hồi quá lâu.")));

        mockMvc.perform(multipart("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}/transcribe", owner.workspace().getId(), sessionId)
                        .file(file("clip.webm", "audio/webm", "abc"))
                        .param("durationMs", "1000")
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.asrStatus").value("TIMEOUT"))
                .andExpect(jsonPath("$.data.transcript").doesNotExist())
                .andExpect(jsonPath("$.data.asrWarnings[0].code").value("ASR_SERVICE_TIMEOUT"));

        assertSafety(cash, before);
        assertThat(voiceSessionDraftRepository.findAllByVoiceSessionIdOrderByDraftIndexAsc(UUID.fromString(sessionId))).isEmpty();
    }

    @Test
    void transcribeThenInterpretCreatesDraftsOnlyAfterInterpret() throws Exception {
        TestUser owner = registerAndLogin("vasr_chain");
        Wallet cash = wallet(owner.workspace(), "Cash");
        Category food = category(owner.workspace(), "Ăn uống", CategoryType.EXPENSE);
        keyword(owner.workspace(), food, "ăn");
        String sessionId = createSession(owner);
        Counts before = counts(cash);

        transcribe(owner, sessionId);
        assertThat(voiceSessionDraftRepository.findAllByVoiceSessionIdOrderByDraftIndexAsc(UUID.fromString(sessionId))).isEmpty();

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}/interpret", owner.workspace().getId(), sessionId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("TRANSACTION_REVIEW"))
                .andExpect(jsonPath("$.data.drafts.length()").value(1))
                .andExpect(jsonPath("$.data.drafts[0].type").value("EXPENSE"))
                .andExpect(jsonPath("$.data.drafts[0].categoryId").value(food.getId().toString()));

        assertSafety(cash, before);
    }

    @Test
    void multiIntentAsrTranscriptInterpretsToFourDraftsWithoutPosting() throws Exception {
        TestUser owner = registerAndLogin("vasr_multi");
        Wallet cash = wallet(owner.workspace(), "Cash");
        Category fuel = category(owner.workspace(), "Xăng xe", CategoryType.EXPENSE);
        keyword(owner.workspace(), fuel, "xăng");
        String sessionId = createSession(owner);
        asrClient.result = success("Hôm nay đã kiếm được 800 Tôi ăn hết 50 Cái đổ xăng hết 65.000 Ta gửi tiết kiệm hết 35");
        Counts before = counts(cash);

        transcribe(owner, sessionId);
        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}/interpret", owner.workspace().getId(), sessionId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("MULTI_DRAFT_REVIEW"))
                .andExpect(jsonPath("$.data.drafts.length()").value(4))
                .andExpect(jsonPath("$.data.drafts[0].type").value("INCOME_FACT"))
                .andExpect(jsonPath("$.data.drafts[1].type").value("EXPENSE"))
                .andExpect(jsonPath("$.data.drafts[2].type").value("EXPENSE"))
                .andExpect(jsonPath("$.data.drafts[2].categoryId").value(fuel.getId().toString()))
                .andExpect(jsonPath("$.data.drafts[3].type").value("SAVINGS_ALLOCATION"));

        assertSafety(cash, before);
        assertThat(voiceSessionDraftRepository.findAllByVoiceSessionIdOrderByDraftIndexAsc(UUID.fromString(sessionId)))
                .extracting("type")
                .containsExactly("INCOME_FACT", "EXPENSE", "EXPENSE", "SAVINGS_ALLOCATION");
    }

    private VoiceAsrTranscribeResult success(String transcript) {
        return new VoiceAsrTranscribeResult(
                VoiceAsrProviderType.MOCK,
                VoiceSessionAsrStatus.SUCCEEDED,
                "mock",
                "vi",
                1000L,
                transcript,
                transcript,
                null,
                List.of(new VoiceAsrWarning("ASR_MOCK_TRANSCRIPT", "Mock ASR transcript was returned.")));
    }

    private String createSession(TestUser owner) throws Exception {
        String body = mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-sessions", owner.workspace().getId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sourceType", "AUDIO"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(body);
        return node.path("data").path("sessionId").asText();
    }

    private void transcribe(TestUser owner, String sessionId) throws Exception {
        mockMvc.perform(multipart("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}/transcribe", owner.workspace().getId(), sessionId)
                        .file(file("clip.webm", "audio/webm", "abc"))
                        .param("durationMs", "1000")
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.asrStatus").value("SUCCEEDED"));
    }

    private MockMultipartFile file(String filename, String contentType, String value) {
        return new MockMultipartFile("audio", filename, contentType, value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
        request.setFullName("Voice ASR Test User");
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

    @TestConfiguration
    static class TestAsrConfig {
        @Bean
        @Primary
        StubVoiceAsrClient stubVoiceAsrClient() {
            return new StubVoiceAsrClient();
        }
    }

    static class StubVoiceAsrClient implements VoiceAsrClient {
        private VoiceAsrTranscribeResult result;

        @Override
        public VoiceAsrProviderType provider() {
            return VoiceAsrProviderType.MOCK;
        }

        @Override
        public VoiceAsrTranscribeResult transcribe(VoiceAsrRequest request) {
            return result;
        }
    }

    private record TestUser(User user, Workspace workspace, TokenResponse token) {
    }

    private record Counts(long transactions, long debtPayments, long savingsLedger, long emergencyLedger,
                          long sinkingLedger, long walletSnapshots, BigDecimal walletBalance) {
    }
}
