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
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@ContextConfiguration(initializers = ReceiptSessionOcrAzureIntegrationTests.AzureProperties.class)
class ReceiptSessionOcrAzureIntegrationTests {
    private static final FakeAzureServer AZURE = new FakeAzureServer();

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void resetAzure() {
        AZURE.reset();
    }

    @AfterAll
    static void stopAzure() {
        AZURE.stop();
    }

    @Test
    void azureOcrPersistsMappedFieldsAndDoesNotCreateTransaction() throws Exception {
        TestUser owner = registerAndLogin("receipt_ocr_azure");
        UUID sessionId = createSession(owner);
        upload(owner, sessionId);
        long txBefore = transactionRepository.count();

        mockMvc.perform(post("/api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/ocr", owner.workspace().getId(), sessionId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ocrStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.ocrProvider").value("AZURE_DOCUMENT_INTELLIGENCE"))
                .andExpect(jsonPath("$.data.rawOcrText").isNotEmpty())
                .andExpect(jsonPath("$.data.normalizedOcrText").isNotEmpty())
                .andExpect(jsonPath("$.data.merchantName").value("AZURE MART"))
                .andExpect(jsonPath("$.data.receiptDate").value("2026-08-01"))
                .andExpect(jsonPath("$.data.totalAmount").value(45000))
                .andExpect(jsonPath("$.data.currency").value("VND"))
                .andExpect(jsonPath("$.data.warnings", empty()));

        assertThat(transactionRepository.count()).isEqualTo(txBefore);
        assertThat(AZURE.lastAnalyzeBody).contains("https://cdn.example/receipts/");
    }

    @Test
    void workspaceIsolationBlocksAzureOcr() throws Exception {
        TestUser owner = registerAndLogin("receipt_ocr_azure_owner");
        TestUser other = registerAndLogin("receipt_ocr_azure_other");
        UUID sessionId = createSession(owner);
        upload(owner, sessionId);

        mockMvc.perform(post("/api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/ocr", other.workspace().getId(), sessionId)
                        .header("Authorization", bearer(other.token())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECEIPT_SESSION_NOT_FOUND"));
        assertThat(AZURE.calls).isZero();
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

    private void upload(TestUser user, UUID sessionId) throws Exception {
        mockMvc.perform(multipart("/api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/image", user.workspace().getId(), sessionId)
                        .file(new MockMultipartFile("file", "receipt.jpg", "image/jpeg", new byte[] {1, 2, 3}))
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

    static class AzureProperties implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            AZURE.start();
            TestPropertyValues.of(
                    "MONEYFLOW_RECEIPT_OCR_PROVIDER=azure_document_intelligence",
                    "AZURE_DOCUMENT_INTELLIGENCE_ENDPOINT=" + AZURE.endpoint(),
                    "AZURE_DOCUMENT_INTELLIGENCE_KEY=fake-key",
                    "MONEYFLOW_RECEIPT_OCR_POLL_INTERVAL_MS=1",
                    "MONEYFLOW_RECEIPT_OCR_MAX_POLL_ATTEMPTS=3")
                    .applyTo(context);
        }
    }

    @TestConfiguration
    static class ReceiptStorageTestConfig {
        @Bean
        @Primary
        ReceiptImageStorageService fakeReceiptImageStorageService() {
            return new ReceiptImageStorageService() {
                @Override
                public boolean isEnabled() {
                    return true;
                }

                @Override
                public StoredReceiptImage upload(String objectKey, MultipartFile file) {
                    if (file == null || file.isEmpty()) {
                        throw new BusinessException("RECEIPT_IMAGE_REQUIRED", "Receipt image is required");
                    }
                    return new StoredReceiptImage("test", objectKey, "https://cdn.example/" + objectKey);
                }
            };
        }
    }

    private static class FakeAzureServer {
        private HttpServer server;
        private int calls;
        private String lastAnalyzeBody;

        void start() {
            if (server != null) return;
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                server.createContext("/", this::handle);
                server.start();
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }

        void reset() {
            calls = 0;
            lastAnalyzeBody = null;
        }

        String endpoint() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void stop() {
            if (server != null) server.stop(0);
        }

        private void handle(HttpExchange exchange) throws IOException {
            calls++;
            if ("POST".equals(exchange.getRequestMethod())) {
                lastAnalyzeBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Operation-Location", endpoint() + "/operations/1?api-version=2024-11-30");
                exchange.sendResponseHeaders(202, -1);
                exchange.close();
                return;
            }
            byte[] body = """
                    {"status":"succeeded","analyzeResult":{"content":"HOA DON\\nAZURE MART\\nTotal 45000","documents":[{"fields":{"MerchantName":{"valueString":"AZURE MART","confidence":0.9},"TransactionDate":{"valueDate":"2026-08-01","confidence":0.9},"Total":{"valueCurrency":{"amount":45000,"currencyCode":"VND"},"confidence":0.92}}}]}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }
    }

    private record TestUser(User user, Workspace workspace, TokenResponse token) {
    }
}
