package com.moneyflowbackend;

import com.moneyflowbackend.auth.dto.GoogleLoginRequest;
import com.moneyflowbackend.auth.dto.LoginRequest;
import com.moneyflowbackend.auth.dto.RegisterRequest;
import com.moneyflowbackend.auth.dto.TokenResponse;
import com.moneyflowbackend.auth.google.GoogleTokenPayload;
import com.moneyflowbackend.auth.google.GoogleTokenVerifier;
import com.moneyflowbackend.auth.repository.AuthAccountRepository;
import com.moneyflowbackend.auth.service.AuthService;
import com.moneyflowbackend.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthAccountStatusIntegrationTests {

    @Autowired AuthService authService;
    @Autowired MockMvc mockMvc;
    @Autowired FakeGoogleTokenVerifier googleVerifier;
    @Autowired AuthAccountRepository authAccountRepository;

    @BeforeEach
    void resetVerifier() {
        googleVerifier.reset();
    }

    @Test
    void localOnlyUserReportsGoogleUnlinked() throws Exception {
        authService.register(registerRequest("provider_local", "provider-local@example.com"));
        TokenResponse token = authService.login(loginRequest("provider_local"));

        mockMvc.perform(get("/api/me/auth-accounts")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.googleLinked").value(false))
                .andExpect(jsonPath("$.data.providers[0]").value("LOCAL"))
                .andExpect(jsonPath("$.data.providerSubject").doesNotExist())
                .andExpect(jsonPath("$.data.token").doesNotExist());
    }

    @Test
    void googleLinkedUserReportsGoogleLinkedWithoutSubject() throws Exception {
        authService.register(registerRequest("provider_google", "provider-google@example.com"));
        googleVerifier.add("provider-token", new GoogleTokenPayload("google-provider-sub", "provider-google@example.com", true, "Google User", null));
        TokenResponse token = authService.googleLogin(googleRequest("provider-token"));

        mockMvc.perform(get("/api/me/auth-accounts")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.googleLinked").value(true))
                .andExpect(jsonPath("$.data.providers[?(@ == 'GOOGLE')]").exists())
                .andExpect(jsonPath("$.data.providerSubject").doesNotExist())
                .andExpect(jsonPath("$.data.secret").doesNotExist());
    }

    @Test
    void missingProviderRowsReportGoogleUnlinked() throws Exception {
        authService.register(registerRequest("provider_missing", "provider-missing@example.com"));
        TokenResponse token = authService.login(loginRequest("provider_missing"));
        authAccountRepository.deleteAll(authAccountRepository.findAllByUserId(token.getUser().getId()));
        authAccountRepository.flush();

        mockMvc.perform(get("/api/me/auth-accounts")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.googleLinked").value(false))
                .andExpect(jsonPath("$.data.providers").isEmpty())
                .andExpect(jsonPath("$.data.providerSubject").doesNotExist())
                .andExpect(jsonPath("$.data.token").doesNotExist());
    }

    private RegisterRequest registerRequest(String username, String email) {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(username);
        req.setEmail(email);
        req.setPassword("StrongPassword123");
        req.setFullName("Provider Test User");
        return req;
    }

    private LoginRequest loginRequest(String identifier) {
        LoginRequest req = new LoginRequest();
        req.setIdentifier(identifier);
        req.setPassword("StrongPassword123");
        return req;
    }

    private GoogleLoginRequest googleRequest(String credential) {
        GoogleLoginRequest req = new GoogleLoginRequest();
        req.setCredential(credential);
        return req;
    }

    private String bearer(TokenResponse token) {
        return "Bearer " + token.getAccessToken();
    }

    @TestConfiguration
    static class GoogleTestConfig {
        @Bean
        @Primary
        FakeGoogleTokenVerifier fakeGoogleTokenVerifier() {
            return new FakeGoogleTokenVerifier();
        }
    }

    static class FakeGoogleTokenVerifier implements GoogleTokenVerifier {
        private final Map<String, GoogleTokenPayload> payloads = new HashMap<>();

        void reset() {
            payloads.clear();
        }

        void add(String credential, GoogleTokenPayload payload) {
            payloads.put(credential, payload);
        }

        @Override
        public GoogleTokenPayload verify(String credential) {
            GoogleTokenPayload payload = payloads.get(credential);
            if (payload == null) {
                throw new BusinessException("INVALID_GOOGLE_CREDENTIAL", "Google credential invalid");
            }
            return payload;
        }
    }
}
