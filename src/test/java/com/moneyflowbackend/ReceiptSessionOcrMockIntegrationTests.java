package com.moneyflowbackend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneyflowbackend.auth.dto.LoginRequest;
import com.moneyflowbackend.auth.dto.RegisterRequest;
import com.moneyflowbackend.auth.dto.TokenResponse;
import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.auth.service.AuthService;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.receipt.session.storage.ReceiptImageStorageService;
import com.moneyflowbackend.receipt.session.storage.StoredReceiptImage;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "MONEYFLOW_RECEIPT_OCR_PROVIDER=mock")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReceiptSessionOcrMockIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired FakeReceiptImageStorageService receiptImageStorageService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void resetStorage() {
        receiptImageStorageService.reset();
    }

    @Test
    void mockOcrPersistsTextMetadataAndDoesNotCreateTransaction() throws Exception {
        TestUser owner = registerAndLogin("receipt_ocr_mock");
        UUID sessionId = createSession(owner);
        upload(owner, sessionId, "coffee.jpg");
        long txBefore = transactionRepository.count();

        mockMvc.perform(post("/api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/ocr", owner.workspace().getId(), sessionId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ocrStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.ocrProvider").value("MOCK"))
                .andExpect(jsonPath("$.data.rawOcrText").isNotEmpty())
                .andExpect(jsonPath("$.data.normalizedOcrText").isNotEmpty())
                .andExpect(jsonPath("$.data.merchantName").value("QUAN CA PHE DEMO"))
                .andExpect(jsonPath("$.data.receiptDate").value("2026-08-01"))
                .andExpect(jsonPath("$.data.totalAmount").value(25000))
                .andExpect(jsonPath("$.data.currency").value("VND"))
                .andExpect(jsonPath("$.data.warnings", empty()))
                .andExpect(jsonPath("$.data.nextActions[0]").value("BUILD_DRAFT"));

        assertThat(transactionRepository.count()).isEqualTo(txBefore);
    }

    @Test
    void ocrWithoutImageReturnsWarningAndNoTransaction() throws Exception {
        TestUser owner = registerAndLogin("receipt_ocr_no_image");
        UUID sessionId = createSession(owner);
        long txBefore = transactionRepository.count();

        mockMvc.perform(post("/api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/ocr", owner.workspace().getId(), sessionId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ocrStatus").value("NOT_REQUESTED"))
                .andExpect(jsonPath("$.data.rawOcrText").doesNotExist())
                .andExpect(jsonPath("$.data.warnings[0].code").value("RECEIPT_IMAGE_REQUIRED"));

        assertThat(transactionRepository.count()).isEqualTo(txBefore);
    }

    @Test
    void mockEmptyResultMapsToEmptyTextWarning() throws Exception {
        TestUser owner = registerAndLogin("receipt_ocr_empty");
        UUID sessionId = createSession(owner);
        upload(owner, sessionId, "empty.jpg");

        mockMvc.perform(post("/api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/ocr", owner.workspace().getId(), sessionId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ocrStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.ocrProvider").value("MOCK"))
                .andExpect(jsonPath("$.data.warnings[0].code").value("OCR_EMPTY_TEXT"));
    }

    @Test
    void mockOcrNormalizesUnicodeAndLineBreaks() throws Exception {
        TestUser owner = registerAndLogin("receipt_ocr_unicode");
        UUID sessionId = createSession(owner);
        upload(owner, sessionId, "unicode.jpg");

        String body = mockMvc.perform(post("/api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/ocr", owner.workspace().getId(), sessionId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ocrStatus").value("SUCCEEDED"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        String normalized = objectMapper.readTree(body).path("data").path("normalizedOcrText").asText();
        assertThat(Normalizer.isNormalized(normalized, Normalizer.Form.NFC)).isTrue();
        assertThat(normalized).contains("HÓA DON DEMO");
        assertThat(normalized).doesNotContain("\r");
    }

    @Test
    void workspaceIsolationBlocksOcr() throws Exception {
        TestUser owner = registerAndLogin("receipt_ocr_owner");
        TestUser other = registerAndLogin("receipt_ocr_other");
        UUID sessionId = createSession(owner);
        upload(owner, sessionId, "coffee.jpg");

        mockMvc.perform(post("/api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/ocr", other.workspace().getId(), sessionId)
                        .header("Authorization", bearer(other.token())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECEIPT_SESSION_NOT_FOUND"));
    }

    @Test
    void rerunningMockOcrReplacesCurrentResultWithoutTransaction() throws Exception {
        TestUser owner = registerAndLogin("receipt_ocr_rerun");
        UUID sessionId = createSession(owner);
        upload(owner, sessionId, "coffee.jpg");
        long txBefore = transactionRepository.count();

        mockMvc.perform(post("/api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/ocr", owner.workspace().getId(), sessionId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/ocr", owner.workspace().getId(), sessionId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ocrStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.warnings", empty()));

        assertThat(transactionRepository.count()).isEqualTo(txBefore);
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

    private TestUser registerAndLogin(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        RegisterRequest request = new RegisterRequest();
        request.setUsername(prefix + "_" + suffix);
        request.setEmail(prefix + "_" + suffix + "@example.com");
        request.setPassword("StrongPassword123");
        request.setFullName("Receipt OCR Test User");
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

    @TestConfiguration
    static class ReceiptStorageTestConfig {
        @Bean
        @Primary
        FakeReceiptImageStorageService fakeReceiptImageStorageService() {
            return new FakeReceiptImageStorageService();
        }
    }

    static class FakeReceiptImageStorageService implements ReceiptImageStorageService {
        private boolean failUpload;

        void reset() {
            failUpload = false;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public StoredReceiptImage upload(String objectKey, MultipartFile file) {
            if (failUpload) {
                throw new BusinessException("RECEIPT_STORAGE_FAILED", "provider detail", HttpStatus.BAD_GATEWAY);
            }
            return new StoredReceiptImage("test", "stored/" + objectKey, "https://cdn.example/" + objectKey);
        }
    }

    private record TestUser(User user, Workspace workspace, TokenResponse token) {
    }
}
