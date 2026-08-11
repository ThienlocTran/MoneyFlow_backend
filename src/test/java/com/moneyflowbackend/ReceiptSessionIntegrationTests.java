package com.moneyflowbackend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneyflowbackend.auth.dto.LoginRequest;
import com.moneyflowbackend.auth.dto.RegisterRequest;
import com.moneyflowbackend.auth.dto.TokenResponse;
import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.auth.service.AuthService;
import com.moneyflowbackend.receipt.session.ReceiptSessionRepository;
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
import java.util.UUID;

import com.moneyflowbackend.common.exception.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReceiptSessionIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired ReceiptSessionRepository receiptSessionRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired FakeReceiptImageStorageService receiptImageStorageService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void resetStorage() {
        receiptImageStorageService.reset();
    }

    @Test
    void createSessionStoresReceiptFoundationWithoutTransaction() throws Exception {
        TestUser owner = registerAndLogin("receipt_session_create");
        long txBefore = transactionRepository.count();

        String body = mockMvc.perform(post("/api/workspaces/{workspaceId}/receipt-sessions", owner.workspace().getId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType("application/json")
                        .content("{\"source\":\"UPLOAD\",\"note\":\"paper receipt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.imageStorageStatus").value("NOT_REQUESTED"))
                .andExpect(jsonPath("$.data.ocrStatus").value("NOT_REQUESTED"))
                .andExpect(jsonPath("$.data.nextActions[0]").value("UPLOAD_IMAGE"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(id(body)).isNotNull();
        assertThat(receiptSessionRepository.count()).isEqualTo(1);
        assertThat(transactionRepository.count()).isEqualTo(txBefore);
    }

    @Test
    void uploadValidImageWithStorageDisabledKeepsMetadataAndWarning() throws Exception {
        receiptImageStorageService.enabled = false;
        TestUser owner = registerAndLogin("receipt_session_disabled");
        UUID sessionId = createSession(owner);

        mockMvc.perform(multipart("/api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/image", owner.workspace().getId(), sessionId)
                        .file(image("receipt.jpg", "image/jpeg", 1, 2, 3))
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IMAGE_UPLOADED"))
                .andExpect(jsonPath("$.data.imageStorageStatus").value("STORAGE_NOT_CONFIGURED"))
                .andExpect(jsonPath("$.data.imageContentType").value("image/jpeg"))
                .andExpect(jsonPath("$.data.imageOriginalFilename").value("receipt.jpg"))
                .andExpect(jsonPath("$.data.imageSizeBytes").value(3))
                .andExpect(jsonPath("$.data.imageUrl").doesNotExist())
                .andExpect(jsonPath("$.data.warnings[*].code", containsInAnyOrder("RECEIPT_STORAGE_NOT_CONFIGURED", "OCR_NOT_REQUESTED")))
                .andExpect(jsonPath("$.data.nextActions[0]").value("RUN_OCR"));

        assertThat(receiptImageStorageService.lastObjectKey).isNull();
    }

    @Test
    void uploadValidImageWithStorageSuccessStoresUrlAndDoesNotCreateTransaction() throws Exception {
        TestUser owner = registerAndLogin("receipt_session_stored");
        UUID sessionId = createSession(owner);
        long txBefore = transactionRepository.count();

        mockMvc.perform(multipart("/api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/image", owner.workspace().getId(), sessionId)
                        .file(image("receipt.webp", "image/webp", 1, 2, 3, 4))
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IMAGE_UPLOADED"))
                .andExpect(jsonPath("$.data.imageStorageStatus").value("STORED"))
                .andExpect(jsonPath("$.data.imageUrl").value(startsWith("https://cdn.example/receipts/")));

        assertThat(receiptImageStorageService.lastObjectKey).startsWith("receipts/");
        assertThat(receiptImageStorageService.lastObjectKey).endsWith(".webp");
        assertThat(transactionRepository.count()).isEqualTo(txBefore);
    }

    @Test
    void uploadStorageFailureReturnsSessionWithFailedStorageStatus() throws Exception {
        receiptImageStorageService.failUpload = true;
        TestUser owner = registerAndLogin("receipt_session_storage_failed");
        UUID sessionId = createSession(owner);

        mockMvc.perform(multipart("/api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/image", owner.workspace().getId(), sessionId)
                        .file(image("receipt.png", "image/png", 1))
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IMAGE_UPLOADED"))
                .andExpect(jsonPath("$.data.imageStorageStatus").value("STORAGE_FAILED"))
                .andExpect(jsonPath("$.data.warnings[*].code", containsInAnyOrder("RECEIPT_STORAGE_FAILED", "OCR_NOT_REQUESTED")))
                .andExpect(jsonPath("$.data.nextActions", containsInAnyOrder("RETRY_UPLOAD", "RUN_OCR")));
    }

    @Test
    void invalidImageInputIsRejectedCleanly() throws Exception {
        TestUser owner = registerAndLogin("receipt_session_invalid");
        UUID sessionId = createSession(owner);

        mockMvc.perform(multipart("/api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/image", owner.workspace().getId(), sessionId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECEIPT_IMAGE_REQUIRED"));

        mockMvc.perform(multipart("/api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/image", owner.workspace().getId(), sessionId)
                        .file(image("receipt.pdf", "application/pdf", 1))
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("OCR_UNSUPPORTED_IMAGE_FORMAT"));

        mockMvc.perform(multipart("/api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/image", owner.workspace().getId(), sessionId)
                        .file(new MockMultipartFile("file", "large.jpg", "image/jpeg", new byte[8388609]))
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("OCR_FILE_TOO_LARGE"));
    }

    @Test
    void workspaceIsolationBlocksCrossWorkspaceDetailAndUpload() throws Exception {
        TestUser owner = registerAndLogin("receipt_session_owner");
        TestUser other = registerAndLogin("receipt_session_other");
        UUID sessionId = createSession(owner);

        mockMvc.perform(get("/api/workspaces/{workspaceId}/receipt-sessions/{sessionId}", other.workspace().getId(), sessionId)
                        .header("Authorization", bearer(other.token())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECEIPT_SESSION_NOT_FOUND"));

        mockMvc.perform(multipart("/api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/image", other.workspace().getId(), sessionId)
                        .file(image("receipt.jpg", "image/jpeg", 1))
                        .header("Authorization", bearer(other.token())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECEIPT_SESSION_NOT_FOUND"));
    }

    @Test
    void missingSessionReturnsNotFound() throws Exception {
        TestUser owner = registerAndLogin("receipt_session_missing");

        mockMvc.perform(get("/api/workspaces/{workspaceId}/receipt-sessions/{sessionId}", owner.workspace().getId(), UUID.randomUUID())
                        .header("Authorization", bearer(owner.token())))
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
        return UUID.fromString(id(body));
    }

    private String id(String body) throws Exception {
        return objectMapper.readTree(body).path("data").path("id").asText();
    }

    private TestUser registerAndLogin(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        RegisterRequest request = new RegisterRequest();
        request.setUsername(prefix + "_" + suffix);
        request.setEmail(prefix + "_" + suffix + "@example.com");
        request.setPassword("StrongPassword123");
        request.setFullName("Receipt Session Test User");
        authService.register(request);
        LoginRequest login = new LoginRequest();
        login.setIdentifier(request.getUsername());
        login.setPassword("StrongPassword123");
        TokenResponse token = authService.login(login);
        Workspace workspace = workspaceRepository.findAllByUserId(token.getUser().getId()).getFirst();
        User user = userRepository.getReferenceById(token.getUser().getId());
        return new TestUser(user, workspace, token);
    }

    private MockMultipartFile image(String name, String contentType, int... bytes) {
        byte[] content = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            content[i] = (byte) bytes[i];
        }
        return new MockMultipartFile("file", name, contentType, content);
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
        private String lastObjectKey;
        private boolean enabled = true;
        private boolean failUpload;

        void reset() {
            lastObjectKey = null;
            enabled = true;
            failUpload = false;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public StoredReceiptImage upload(String objectKey, MultipartFile file) {
            if (failUpload) {
                throw new BusinessException("RECEIPT_STORAGE_FAILED", "provider detail", HttpStatus.BAD_GATEWAY);
            }
            lastObjectKey = objectKey;
            return new StoredReceiptImage("test", "stored/" + objectKey, "https://cdn.example/" + objectKey);
        }
    }

    private record TestUser(User user, Workspace workspace, TokenResponse token) {
    }
}
