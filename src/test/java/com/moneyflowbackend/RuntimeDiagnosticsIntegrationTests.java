package com.moneyflowbackend;

import com.moneyflowbackend.auth.dto.LoginRequest;
import com.moneyflowbackend.auth.dto.RegisterRequest;
import com.moneyflowbackend.auth.dto.TokenResponse;
import com.moneyflowbackend.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "VOICE_AUDIO_STORAGE_PROVIDER=cloudinary",
        "MONEYFLOW_AVATAR_STORAGE_PROVIDER=cloudinary",
        "MONEYFLOW_CLOUDINARY_CLOUD_NAME=diagnostics-cloud",
        "MONEYFLOW_CLOUDINARY_API_KEY=diagnostics-api-key",
        "MONEYFLOW_CLOUDINARY_API_SECRET=diagnostics-api-secret",
        "MONEYFLOW_CLOUDINARY_BASE_FOLDER=dev",
        "MONEYFLOW_ASR_PROVIDER=external_http",
        "MONEYFLOW_ASR_SERVICE_URL=https://secret-asr.example/path?token=hidden",
        "MONEYFLOW_ASR_TIMEOUT_SECONDS=60",
        "MONEYFLOW_ASR_LANGUAGE=vi",
        "AZURE_SPEECH_LANGUAGE=vi"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RuntimeDiagnosticsIntegrationTests {
    @Autowired AuthService authService;
    @Autowired MockMvc mockMvc;

    @Test
    void unauthenticatedReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/me/runtime-diagnostics"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void authenticatedReturnsSafeRuntimeDiagnostics() throws Exception {
        TokenResponse token = createUser("diagnostics_ok");

        mockMvc.perform(get("/api/me/runtime-diagnostics")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.backend.status").value("UP"))
                .andExpect(jsonPath("$.data.backend.environmentRoot").value("dev"))
                .andExpect(jsonPath("$.data.database.ready").value(true))
                .andExpect(jsonPath("$.data.storage.voiceAudio.provider").value("cloudinary"))
                .andExpect(jsonPath("$.data.storage.voiceAudio.enabled").value(true))
                .andExpect(jsonPath("$.data.storage.voiceAudio.configured").value(true))
                .andExpect(jsonPath("$.data.storage.voiceAudio.cloudNamePresent").value(true))
                .andExpect(jsonPath("$.data.storage.voiceAudio.apiKeyPresent").value(true))
                .andExpect(jsonPath("$.data.storage.voiceAudio.apiSecretPresent").value(true))
                .andExpect(jsonPath("$.data.storage.voiceAudio.baseFolder").value("dev"))
                .andExpect(jsonPath("$.data.storage.voiceAudio.maxBytes").value(10485760))
                .andExpect(jsonPath("$.data.storage.voiceAsr.provider").value("EXTERNAL_HTTP"))
                .andExpect(jsonPath("$.data.storage.voiceAsr.enabled").value(true))
                .andExpect(jsonPath("$.data.storage.voiceAsr.configured").value(true))
                .andExpect(jsonPath("$.data.storage.voiceAsr.serviceUrlConfigured").value(true))
                .andExpect(jsonPath("$.data.storage.voiceAsr.timeoutSeconds").value(60))
                .andExpect(jsonPath("$.data.storage.voiceAsr.language").value("vi"))
                .andExpect(jsonPath("$.data.storage.avatar.provider").value("cloudinary"))
                .andExpect(jsonPath("$.data.storage.avatar.configured").value(true))
                .andExpect(jsonPath("$.data.storage.receiptOcr.provider").value("NONE"))
                .andExpect(jsonPath("$.data.storage.receiptOcr.enabled").value(false))
                .andExpect(jsonPath("$.data.storage.receiptOcr.configured").value(false))
                .andExpect(jsonPath("$.data.storage.receiptOcr.maxImages").value(5))
                .andExpect(jsonPath("$.data.storage.receiptOcr.maxImageBytes").value(5242880))
                .andExpect(jsonPath("$.data.storage.receiptOcr.timeoutSeconds").value(30))
                .andExpect(jsonPath("$.data.storage.receiptOcr.language").value("vi"))
                .andExpect(jsonPath("$.data.storage.receiptOcr.serviceUrlConfigured").value(false))
                .andExpect(jsonPath("$.data.storage.receiptOcr.externalServiceConfigured").value(false))
                .andExpect(jsonPath("$.data.security.authenticated").value(true))
                .andExpect(content().string(not(containsString("diagnostics-api-key"))))
                .andExpect(content().string(not(containsString("diagnostics-api-secret"))))
                .andExpect(content().string(not(containsString("diagnostics-cloud"))))
                .andExpect(content().string(not(containsString("secret-asr.example"))))
                .andExpect(content().string(not(containsString("hidden"))));
    }

    private TokenResponse createUser(String username) {
        authService.register(registerRequest(username));
        return authService.login(loginRequest(username));
    }

    private RegisterRequest registerRequest(String username) {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(username);
        req.setEmail(username + "@example.com");
        req.setPassword("StrongPassword123");
        req.setFullName("Test User");
        return req;
    }

    private LoginRequest loginRequest(String username) {
        LoginRequest req = new LoginRequest();
        req.setIdentifier(username);
        req.setPassword("StrongPassword123");
        return req;
    }

    private String bearer(TokenResponse token) {
        return "Bearer " + token.getAccessToken();
    }
}
