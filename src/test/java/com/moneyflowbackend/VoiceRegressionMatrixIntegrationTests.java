package com.moneyflowbackend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
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
import com.moneyflowbackend.transaction.model.Transaction;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.voice.repository.VoiceRecordRepository;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.model.WalletType;
import com.moneyflowbackend.wallet.repository.WalletRepository;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class VoiceRegressionMatrixIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired CategoryKeywordRepository categoryKeywordRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired VoiceRecordRepository voiceRecordRepository;
    @Autowired EntityManager entityManager;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void voiceReviewDomainMatrixDoesNotMisclassifySensitiveDrafts() throws Exception {
        TestUser owner = registerAndLogin("voice_regression_matrix");
        Wallet mb = wallet(owner.workspace(), "MB");
        Wallet cake = wallet(owner.workspace(), "Cake");
        Category food = category(owner.workspace(), "Food");
        keyword(owner.workspace(), food, "an");
        long txBefore = transactionRepository.count();
        long debtsBefore = count("debts");
        long paymentsBefore = count("debt_payments");

        assertVoiceReview(owner, "toi an het 50.000", "EXPENSE", "50000")
                .andExpect(jsonPath("$.data.candidate.type").value("EXPENSE"));
        assertVoiceReview(owner, "toi nhan luong 800k vao Cake", "INCOME", "800000")
                .andExpect(jsonPath("$.data.candidate.walletId").value(cake.getId().toString()))
                .andExpect(jsonPath("$.data.candidate.walletRequired").value(true));
        assertVoiceReview(owner, "hom nay toi kiem duoc 800", "INCOME_FACT", "800000")
                .andExpect(jsonPath("$.data.candidate.affectsWalletBalance").value(false))
                .andExpect(jsonPath("$.data.candidate.needsFields").isArray());
        assertVoiceReview(owner, "MB con 4 trieu 8", "WALLET_SNAPSHOT", "4800000")
                .andExpect(jsonPath("$.data.candidate.walletId").value(mb.getId().toString()))
                .andExpect(jsonPath("$.data.candidate.affectsWalletBalance").value(false));
        assertVoiceReview(owner, "toi gui tiet kiem 72.000", "SAVINGS_ALLOCATION", "72000")
                .andExpect(jsonPath("$.data.candidate.countsAsExpense").value(false))
                .andExpect(jsonPath("$.data.candidate.categoryRequired").value(false));
        assertDebtVoice(owner, "toi cho Nam muon 500k", "LOAN_DISBURSEMENT", "RECEIVABLE", "500000");
        assertDebtVoice(owner, "Nam tra toi 200k", "LOAN_COLLECTION", "RECEIVABLE", "200000");
        assertDebtVoice(owner, "toi muon Nam 1 trieu", "BORROWING_RECEIPT", "PAYABLE", "1000000");
        assertDebtVoice(owner, "toi tra no Nam 100k", "BORROWING_REPAYMENT", "PAYABLE", "100000");

        assertThat(transactionRepository.count()).isEqualTo(txBefore);
        assertThat(count("debts")).isEqualTo(debtsBefore);
        assertThat(count("debt_payments")).isEqualTo(paymentsBefore);
    }

    @Test
    void voiceCommandReadOnlyAndMixedMatrixDoesNotMutateLedger() throws Exception {
        TestUser owner = registerAndLogin("voice_regression_command");
        Wallet mb = wallet(owner.workspace(), "MB");
        Category food = category(owner.workspace(), "Food");
        keyword(owner.workspace(), food, "an");
        long txBefore = transactionRepository.count();
        long voiceBefore = voiceRecordRepository.count();

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-command/interpret", owner.workspace().getId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("text", "thang nay toi tieu bao nhieu?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("READ_ONLY_QUERY"))
                .andExpect(jsonPath("$.data.commandType").value("READ_ONLY_QUERY"));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-command/interpret", owner.workspace().getId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("text", "Nam con no toi bao nhieu?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("READ_ONLY_QUERY"))
                .andExpect(jsonPath("$.data.query.intent").value("RECEIVABLE_SUMMARY"));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-command/interpret", owner.workspace().getId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("text", "hom nay kiem duoc 800, MB con 4tr8, toi an 50k"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("MULTI_DRAFT_REVIEW"))
                .andExpect(jsonPath("$.data.drafts[0].candidate.type").value("INCOME_FACT"))
                .andExpect(jsonPath("$.data.drafts[1].candidate.type").value("WALLET_SNAPSHOT"))
                .andExpect(jsonPath("$.data.drafts[1].candidate.walletId").value(mb.getId().toString()))
                .andExpect(jsonPath("$.data.drafts[2].candidate.type").value("EXPENSE"));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-command/interpret", owner.workspace().getId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("text", "toi an 50k, thang nay tieu bao nhieu?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("NEEDS_CLARIFICATION"))
                .andExpect(jsonPath("$.data.warnings[0].code").value("VOICE_COMMAND_MIXED_QUERY_AND_DRAFT"));

        assertThat(transactionRepository.count()).isEqualTo(txBefore);
        assertThat(voiceRecordRepository.count()).isEqualTo(voiceBefore + 1);
    }

    @Test
    void receiptMatrixParsesDraftsWithoutPostingOrFakeOcr() throws Exception {
        TestUser owner = registerAndLogin("voice_regression_receipt");
        long txBefore = transactionRepository.count();
        String receipt = """
                CO.OPMART
                Sua tuoi 25.000
                Banh mi 15.000
                Tong cong 40.000
                """;

        mockMvc.perform(post("/api/workspaces/{workspaceId}/receipt-review/parse", owner.workspace().getId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("rawText", receipt))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("RECEIPT_REVIEW"))
                .andExpect(jsonPath("$.data.status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.data.candidate.type").value("EXPENSE"))
                .andExpect(jsonPath("$.data.candidate.amount").value(40000));

        mockMvc.perform(multipart("/api/workspaces/{workspaceId}/receipt-review/parse-with-images", owner.workspace().getId())
                        .file(new MockMultipartFile("images", "receipt.jpg", "image/jpeg", new byte[] {1, 2, 3}))
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OCR_NOT_CONFIGURED"))
                .andExpect(jsonPath("$.data.ocr.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.warnings[0].code").value("RECEIPT_OCR_NOT_CONFIGURED"));

        assertThat(transactionRepository.count()).isEqualTo(txBefore);
    }

    private org.springframework.test.web.servlet.ResultActions assertVoiceReview(TestUser owner, String text, String type, String amount) throws Exception {
        return mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-review/parse", owner.workspace().getId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("text", text))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.data.candidate.type").value(type))
                .andExpect(jsonPath("$.data.candidate.amount").value(Integer.parseInt(amount)));
    }

    private void assertDebtVoice(TestUser owner, String text, String type, String direction, String amount) throws Exception {
        assertVoiceReview(owner, text, type, amount)
                .andExpect(jsonPath("$.data.candidate.debtDirection").value(direction))
                .andExpect(jsonPath("$.data.candidate.countsAsIncome").value(false))
                .andExpect(jsonPath("$.data.candidate.countsAsExpense").value(false))
                .andExpect(jsonPath("$.data.candidate.categoryRequired").value(false))
                .andExpect(jsonPath("$.data.drafts[0].warnings[?(@.code == 'DEBT_CONFIRM_NOT_SUPPORTED')]").exists());
    }

    private TestUser registerAndLogin(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        RegisterRequest request = new RegisterRequest();
        request.setUsername(prefix + "_" + suffix);
        request.setEmail(prefix + "_" + suffix + "@example.com");
        request.setPassword("StrongPassword123");
        request.setFullName("Voice Regression Test User");
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

    private Category category(Workspace workspace, String name) {
        return categoryRepository.saveAndFlush(Category.builder()
                .workspace(workspace)
                .name(name)
                .categoryType(CategoryType.EXPENSE)
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

    private long count(String table) {
        return ((Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM " + table).getSingleResult()).longValue();
    }

    private String bearer(TokenResponse token) {
        return "Bearer " + token.getAccessToken();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private record TestUser(User user, Workspace workspace, TokenResponse token) {}
}
