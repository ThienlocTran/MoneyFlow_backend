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
import com.moneyflowbackend.transaction.repository.TransactionRepository;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "MONEYFLOW_RECEIPT_OCR_PROVIDER=none",
        "MONEYFLOW_RECEIPT_OCR_MAX_IMAGES=2",
        "MONEYFLOW_RECEIPT_OCR_MAX_IMAGE_BYTES=8"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReceiptReviewMultipartIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired CategoryKeywordRepository categoryKeywordRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired WalletBalanceService walletBalanceService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void unauthorizedMultipartRequestIsRejected() throws Exception {
        TestUser owner = registerAndLogin("receipt_multipart_unauth");

        mockMvc.perform(multipart("/api/workspaces/{workspaceId}/receipt-review/parse-with-images", owner.workspace().getId())
                        .file(image("receipt.jpg", "image/jpeg", 1, 2))
                        .param("rawText", receiptText()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rawTextOnlyMultipartParsesWithoutImages() throws Exception {
        TestUser owner = registerAndLogin("receipt_multipart_text");

        mockMvc.perform(multipart("/api/workspaces/{workspaceId}/receipt-review/parse-with-images", owner.workspace().getId())
                        .header("Authorization", bearer(owner.token()))
                        .param("rawText", receiptText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.data.imageCount").value(0))
                .andExpect(jsonPath("$.data.ocr.status").value("SKIPPED"))
                .andExpect(jsonPath("$.data.candidate.type").value("EXPENSE"))
                .andExpect(jsonPath("$.data.candidate.amount").value(40000));
    }

    @Test
    void rawTextWithTwoImagesReturnsMetadataAndDoesNotMutateLedger() throws Exception {
        TestUser owner = registerAndLogin("receipt_multipart_images");
        Wallet cash = wallet(owner.workspace(), "Cash");
        Category food = category(owner.workspace(), "Food");
        keyword(owner.workspace(), food, "cafe");
        long txBefore = transactionRepository.count();
        BigDecimal balanceBefore = walletBalanceService.calculateCurrentBalance(cash.getId());

        mockMvc.perform(multipart("/api/workspaces/{workspaceId}/receipt-review/parse-with-images", owner.workspace().getId())
                        .file(image("a.jpg", "image/jpeg", 1, 2))
                        .file(image("b.png", "image/png", 3, 4))
                        .header("Authorization", bearer(owner.token()))
                        .param("rawText", receiptText())
                        .param("walletId", cash.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageCount").value(2))
                .andExpect(jsonPath("$.data.attachments[0].index").value(0))
                .andExpect(jsonPath("$.data.attachments[0].storageStatus").value("NOT_STORED"))
                .andExpect(jsonPath("$.data.attachments[1].contentType").value("image/png"))
                .andExpect(jsonPath("$.data.candidate.walletId").value(cash.getId().toString()))
                .andExpect(jsonPath("$.data.candidate.categoryId").value(food.getId().toString()))
                .andExpect(jsonPath("$.data.candidate.affectsWalletBalance").value(true));

        assertThat(transactionRepository.count()).isEqualTo(txBefore);
        assertThat(walletBalanceService.calculateCurrentBalance(cash.getId())).isEqualByComparingTo(balanceBefore);
    }

    @Test
    void imagesOnlyWithNoOcrReturnsNeedsText() throws Exception {
        TestUser owner = registerAndLogin("receipt_multipart_no_ocr");

        mockMvc.perform(multipart("/api/workspaces/{workspaceId}/receipt-review/parse-with-images", owner.workspace().getId())
                        .file(image("receipt.jpg", "image/jpeg", 1, 2))
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OCR_NOT_CONFIGURED"))
                .andExpect(jsonPath("$.data.source").value("PHOTO_PENDING_OCR"))
                .andExpect(jsonPath("$.data.imageCount").value(1))
                .andExpect(jsonPath("$.data.ocr.provider").value("NONE"))
                .andExpect(jsonPath("$.data.ocr.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.candidate").doesNotExist())
                .andExpect(jsonPath("$.data.warnings[0].code").value("RECEIPT_OCR_NOT_CONFIGURED"));
    }

    @Test
    void invalidImagesAreRejectedCleanly() throws Exception {
        TestUser owner = registerAndLogin("receipt_multipart_invalid");

        mockMvc.perform(multipart("/api/workspaces/{workspaceId}/receipt-review/parse-with-images", owner.workspace().getId())
                        .file(image("a.jpg", "image/jpeg", 1))
                        .file(image("b.jpg", "image/jpeg", 2))
                        .file(image("c.jpg", "image/jpeg", 3))
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECEIPT_TOO_MANY_IMAGES"));

        mockMvc.perform(multipart("/api/workspaces/{workspaceId}/receipt-review/parse-with-images", owner.workspace().getId())
                        .file(image("large.jpg", "image/jpeg", 1, 2, 3, 4, 5, 6, 7, 8, 9))
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECEIPT_IMAGE_TOO_LARGE"));

        mockMvc.perform(multipart("/api/workspaces/{workspaceId}/receipt-review/parse-with-images", owner.workspace().getId())
                        .file(image("receipt.pdf", "application/pdf", 1, 2))
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECEIPT_IMAGE_TYPE_UNSUPPORTED"));

        mockMvc.perform(multipart("/api/workspaces/{workspaceId}/receipt-review/parse-with-images", owner.workspace().getId())
                        .file(image("empty.jpg", "image/jpeg"))
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECEIPT_IMAGE_REQUIRED"));
    }

    @Test
    void crossWorkspaceWalletIsRejected() throws Exception {
        TestUser owner = registerAndLogin("receipt_multipart_refs");
        TestUser other = registerAndLogin("receipt_multipart_refs_other");
        Wallet otherWallet = wallet(other.workspace(), "Other");

        mockMvc.perform(multipart("/api/workspaces/{workspaceId}/receipt-review/parse-with-images", owner.workspace().getId())
                        .header("Authorization", bearer(owner.token()))
                        .param("rawText", receiptText())
                        .param("walletId", otherWallet.getId().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));
    }

    private TestUser registerAndLogin(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        RegisterRequest request = new RegisterRequest();
        request.setUsername(prefix + "_" + suffix);
        request.setEmail(prefix + "_" + suffix + "@example.com");
        request.setPassword("StrongPassword123");
        request.setFullName("Receipt Multipart Test User");
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

    private MockMultipartFile image(String name, String contentType, int... bytes) {
        byte[] content = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            content[i] = (byte) bytes[i];
        }
        return new MockMultipartFile("images", name, contentType, content);
    }

    private String receiptText() {
        return """
                Highlands Coffee
                Date 2026-08-01
                Cafe 40.000
                Total 40.000
                """;
    }

    private String bearer(TokenResponse token) {
        return "Bearer " + token.getAccessToken();
    }

    @SuppressWarnings("unused")
    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private record TestUser(User user, Workspace workspace, TokenResponse token) {
    }
}
