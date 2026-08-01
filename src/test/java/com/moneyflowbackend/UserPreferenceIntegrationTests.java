package com.moneyflowbackend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneyflowbackend.auth.dto.RegisterRequest;
import com.moneyflowbackend.auth.dto.TokenResponse;
import com.moneyflowbackend.auth.service.AuthService;
import com.moneyflowbackend.userpreferences.repository.UserPreferenceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserPreferenceIntegrationTests {
    @Autowired AuthService authService;
    @Autowired UserPreferenceRepository preferenceRepository;
    @Autowired MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void getCreatesDefaults() throws Exception {
        TokenResponse token = createUser("prefs_defaults");

        mockMvc.perform(get("/api/me/preferences")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locale").value("vi-VN"))
                .andExpect(jsonPath("$.data.onboardingWelcomeSeen").value(false))
                .andExpect(jsonPath("$.data.onboardingMainTourCompleted").value(false))
                .andExpect(jsonPath("$.data.onboardingMainTourSkipped").value(false))
                .andExpect(jsonPath("$.data.onboardingVersion").value("2026-07"));

        assertThat(preferenceRepository.findById(token.getUser().getId())).isPresent();
    }

    @Test
    void patchLocalePersists() throws Exception {
        TokenResponse token = createUser("prefs_locale");

        patchPrefs(token, Map.of("locale", "en-US"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locale").value("en-US"));

        mockMvc.perform(get("/api/me/preferences")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locale").value("en-US"));
    }

    @Test
    void patchOnboardingCompletedPersists() throws Exception {
        TokenResponse token = createUser("prefs_done");

        patchPrefs(token, Map.of(
                        "onboardingWelcomeSeen", true,
                        "onboardingMainTourCompleted", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingWelcomeSeen").value(true))
                .andExpect(jsonPath("$.data.onboardingMainTourCompleted").value(true))
                .andExpect(jsonPath("$.data.onboardingMainTourSkipped").value(false));
    }

    @Test
    void partialPatchPreservesFields() throws Exception {
        TokenResponse token = createUser("prefs_partial");

        patchPrefs(token, Map.of("locale", "en-US"))
                .andExpect(status().isOk());
        patchPrefs(token, Map.of("onboardingWelcomeSeen", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locale").value("en-US"))
                .andExpect(jsonPath("$.data.onboardingWelcomeSeen").value(true));
    }

    @Test
    void invalidLocaleReturnsBadRequest() throws Exception {
        TokenResponse token = createUser("prefs_bad_locale");

        patchPrefs(token, Map.of("locale", "fr-FR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LOCALE"));
    }

    @Test
    void unauthenticatedRequestsReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/me/preferences"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(patch("/api/me/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private org.springframework.test.web.servlet.ResultActions patchPrefs(TokenResponse token, Map<String, Object> body) throws Exception {
        return mockMvc.perform(patch("/api/me/preferences")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
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

    private com.moneyflowbackend.auth.dto.LoginRequest loginRequest(String username) {
        com.moneyflowbackend.auth.dto.LoginRequest req = new com.moneyflowbackend.auth.dto.LoginRequest();
        req.setIdentifier(username);
        req.setPassword("StrongPassword123");
        return req;
    }

    private String bearer(TokenResponse token) {
        return "Bearer " + token.getAccessToken();
    }
}
