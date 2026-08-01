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
import com.moneyflowbackend.transaction.model.Transaction;
import com.moneyflowbackend.transaction.model.TransactionSourceType;
import com.moneyflowbackend.transaction.model.TransactionStatus;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.voice.repository.VoiceRecordRepository;
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
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class VoiceCommandIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired CategoryKeywordRepository categoryKeywordRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired VoiceRecordRepository voiceRecordRepository;
    @Autowired WalletBalanceService walletBalanceService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void unauthorizedRequestIsRejected() throws Exception {
        TestUser owner = registerAndLogin("voice_command_unauth");

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-command/interpret", owner.workspace().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("text", "thang nay toi tieu bao nhieu?"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readOnlyQueryRoutesWithoutCreatingDraftOrTransaction() throws Exception {
        TestUser owner = registerAndLogin("voice_command_query");
        Wallet cash = wallet(owner.workspace(), "Cash");
        Category food = category(owner.workspace(), "Food", CategoryType.EXPENSE);
        transaction(owner.workspace(), owner.user(), cash, food, "50000", LocalDate.of(2026, 8, 1));
        long txBefore = transactionRepository.count();
        long voiceBefore = voiceRecordRepository.count();
        BigDecimal balanceBefore = walletBalanceService.calculateCurrentBalance(cash.getId());

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-command/interpret", owner.workspace().getId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "text", "thang nay toi tieu bao nhieu?",
                                "timezone", "Asia/Ho_Chi_Minh",
                                "now", "2026-08-01T08:49:00+07:00",
                                "source", "TEXT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("READ_ONLY_QUERY"))
                .andExpect(jsonPath("$.data.status").value("ANSWERED"))
                .andExpect(jsonPath("$.data.commandType").value("READ_ONLY_QUERY"))
                .andExpect(jsonPath("$.data.query.intent").value("MONTH_EXPENSE_TOTAL"))
                .andExpect(jsonPath("$.data.query.metrics[0].amount").value(50000))
                .andExpect(jsonPath("$.data.warnings[0].code").value("VOICE_COMMAND_ROUTED_TO_QUERY"));

        assertThat(transactionRepository.count()).isEqualTo(txBefore);
        assertThat(voiceRecordRepository.count()).isEqualTo(voiceBefore);
        assertThat(walletBalanceService.calculateCurrentBalance(cash.getId())).isEqualByComparingTo(balanceBefore);
    }

    @Test
    void transactionCommandRoutesToReviewDraftWithoutPosting() throws Exception {
        TestUser owner = registerAndLogin("voice_command_tx");
        wallet(owner.workspace(), "Cash");
        Category food = category(owner.workspace(), "Food", CategoryType.EXPENSE);
        keyword(owner.workspace(), food, "an");
        long txBefore = transactionRepository.count();

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-command/interpret", owner.workspace().getId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("text", "hom nay toi an het 50k"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("TRANSACTION_REVIEW"))
                .andExpect(jsonPath("$.data.status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.data.commandType").value("LEDGER_DRAFT"))
                .andExpect(jsonPath("$.data.review.candidate.type").value("EXPENSE"))
                .andExpect(jsonPath("$.data.review.candidate.categoryId").value(food.getId().toString()));

        assertThat(transactionRepository.count()).isEqualTo(txBefore);
    }

    @Test
    void incomeFactAndWalletSnapshotRouteToDomainReviewModes() throws Exception {
        TestUser owner = registerAndLogin("voice_command_domain");
        Wallet mb = wallet(owner.workspace(), "MB Bank");

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-command/interpret", owner.workspace().getId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("text", "hom nay kiem duoc 800"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("INCOME_FACT_REVIEW"))
                .andExpect(jsonPath("$.data.review.candidate.type").value("INCOME_FACT"))
                .andExpect(jsonPath("$.data.review.candidate.walletId").doesNotExist())
                .andExpect(jsonPath("$.data.review.candidate.affectsWalletBalance").value(false));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-command/interpret", owner.workspace().getId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("text", "MB con 4 trieu 8"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("WALLET_SNAPSHOT_REVIEW"))
                .andExpect(jsonPath("$.data.review.candidate.type").value("WALLET_SNAPSHOT"))
                .andExpect(jsonPath("$.data.review.candidate.walletId").value(mb.getId().toString()))
                .andExpect(jsonPath("$.data.review.candidate.affectsWalletBalance").value(false));
    }

    @Test
    void multiDraftAndUnsupportedCommandsStaySafe() throws Exception {
        TestUser owner = registerAndLogin("voice_command_multi");
        Wallet mb = wallet(owner.workspace(), "MB Bank");
        Category food = category(owner.workspace(), "Food", CategoryType.EXPENSE);
        keyword(owner.workspace(), food, "an");
        long txBefore = transactionRepository.count();

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
                        .content(json(Map.of("text", "toi gui tiet kiem 72k"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("UNSUPPORTED"))
                .andExpect(jsonPath("$.data.status").value("UNSUPPORTED"))
                .andExpect(jsonPath("$.data.review.candidate.type").value("SAVINGS"))
                .andExpect(jsonPath("$.data.warnings[0].code").value("VOICE_COMMAND_UNSUPPORTED"));

        assertThat(transactionRepository.count()).isEqualTo(txBefore);
    }

    @Test
    void mixedQueryAndDraftNeedsClarificationWithoutCreatingRows() throws Exception {
        TestUser owner = registerAndLogin("voice_command_mixed");
        long txBefore = transactionRepository.count();
        long voiceBefore = voiceRecordRepository.count();

        mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-command/interpret", owner.workspace().getId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("text", "hom nay an 50k, thang nay tieu bao nhieu?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("NEEDS_CLARIFICATION"))
                .andExpect(jsonPath("$.data.status").value("NEEDS_CLARIFICATION"))
                .andExpect(jsonPath("$.data.commandType").value("NEEDS_CLARIFICATION"))
                .andExpect(jsonPath("$.data.warnings[0].code").value("VOICE_COMMAND_MIXED_QUERY_AND_DRAFT"));

        assertThat(transactionRepository.count()).isEqualTo(txBefore);
        assertThat(voiceRecordRepository.count()).isEqualTo(voiceBefore);
    }

    private TestUser registerAndLogin(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        RegisterRequest request = new RegisterRequest();
        request.setUsername(prefix + "_" + suffix);
        request.setEmail(prefix + "_" + suffix + "@example.com");
        request.setPassword("StrongPassword123");
        request.setFullName("Voice Command Test User");
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

    private String bearer(TokenResponse token) {
        return "Bearer " + token.getAccessToken();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private record TestUser(User user, Workspace workspace, TokenResponse token) {
    }
}
