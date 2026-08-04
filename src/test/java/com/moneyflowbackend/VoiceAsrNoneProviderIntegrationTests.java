package com.moneyflowbackend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneyflowbackend.auth.dto.LoginRequest;
import com.moneyflowbackend.auth.dto.RegisterRequest;
import com.moneyflowbackend.auth.dto.TokenResponse;
import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.auth.service.AuthService;
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

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "MONEYFLOW_ASR_PROVIDER=none")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class VoiceAsrNoneProviderIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void noneProviderReturnsNotConfiguredWithoutCallingAsr() throws Exception {
        TestUser owner = registerAndLogin("vasr_none");
        String sessionId = createSession(owner);

        mockMvc.perform(multipart("/api/workspaces/{workspaceId}/voice-sessions/{sessionId}/transcribe", owner.workspace().getId(), sessionId)
                        .file(new MockMultipartFile("audio", "clip.webm", "audio/webm", "abc".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.asrStatus").value("NOT_REQUESTED"))
                .andExpect(jsonPath("$.data.transcript").doesNotExist())
                .andExpect(jsonPath("$.data.asrWarnings[0].code").value("ASR_NOT_CONFIGURED"));
    }

    private String createSession(TestUser owner) throws Exception {
        String body = mockMvc.perform(post("/api/workspaces/{workspaceId}/voice-sessions", owner.workspace().getId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("sourceType", "AUDIO"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(body);
        return node.path("data").path("sessionId").asText();
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

    private String bearer(TokenResponse token) {
        return "Bearer " + token.getAccessToken();
    }

    private record TestUser(User user, Workspace workspace, TokenResponse token) {
    }
}
