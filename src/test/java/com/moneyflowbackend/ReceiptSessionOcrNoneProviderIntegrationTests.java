package com.moneyflowbackend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneyflowbackend.auth.dto.LoginRequest;
import com.moneyflowbackend.auth.dto.RegisterRequest;
import com.moneyflowbackend.auth.dto.TokenResponse;
import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.auth.service.AuthService;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
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

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "MONEYFLOW_RECEIPT_OCR_PROVIDER=none")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReceiptSessionOcrNoneProviderIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void providerNoneReturnsNotConfiguredWithoutTransaction() throws Exception {
        TestUser owner = registerAndLogin("receipt_ocr_none");
        UUID sessionId = createSession(owner);
        upload(owner, sessionId);
        long txBefore = transactionRepository.count();

        mockMvc.perform(post("/api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/ocr", owner.workspace().getId(), sessionId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ocrStatus").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$.data.warnings[0].code").value("OCR_NOT_CONFIGURED"));

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

    private void upload(TestUser user, UUID sessionId) throws Exception {
        mockMvc.perform(multipart("/api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/image", user.workspace().getId(), sessionId)
                        .file(new MockMultipartFile("file", "receipt.jpg", "image/jpeg", new byte[] {1}))
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

    private record TestUser(User user, Workspace workspace, TokenResponse token) {
    }
}
