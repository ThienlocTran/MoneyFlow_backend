package com.moneyflowbackend;

import com.moneyflowbackend.auth.dto.LoginRequest;
import com.moneyflowbackend.auth.dto.RegisterRequest;
import com.moneyflowbackend.auth.dto.TokenResponse;
import com.moneyflowbackend.auth.dto.UserResponse;
import com.moneyflowbackend.auth.service.AuthService;
import com.moneyflowbackend.common.exception.GlobalExceptionHandler;
import com.moneyflowbackend.common.security.LogRedactor;
import com.moneyflowbackend.config.SecurityConfig;
import com.moneyflowbackend.security.JwtAuthenticationFilter;
import com.moneyflowbackend.security.RateLimitFilter;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityHardeningTests {
    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired Clock clock;
    @Autowired JwtAuthenticationFilter jwtAuthenticationFilter;
    @Autowired RateLimitFilter rateLimitFilter;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @Test
    void rateLimit_loginTooManyRequestsReturns429() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/public/auth/login")
                    .with(request -> { request.setRemoteAddr("10.0.0.10"); return request; })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("identifier", "missing", "password", "StrongPassword123"))));
        }

        mockMvc.perform(post("/api/public/auth/login")
                .with(request -> { request.setRemoteAddr("10.0.0.10"); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("identifier", "missing", "password", "StrongPassword123"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.message").value("Bạn thao tác quá nhanh. Vui lòng thử lại sau."))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber());
    }

    @Test
    void rateLimit_registerTooManyRequestsReturns429() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/public/auth/register")
                    .with(request -> { request.setRemoteAddr("10.0.0.11"); return request; })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("username", "rl_reg_" + i, "email", "rl_reg_" + i + "@example.com", "password", "StrongPassword123", "fullName", "Rate Limit"))));
        }

        mockMvc.perform(post("/api/public/auth/register")
                .with(request -> { request.setRemoteAddr("10.0.0.11"); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("username", "rl_reg_last", "email", "rl_reg_last@example.com", "password", "StrongPassword123", "fullName", "Rate Limit"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void rateLimit_invitationTooManyRequestsReturns429() throws Exception {
        AuthContext ctx = authContext("rl_invite");
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(post("/api/workspaces/" + ctx.workspaceId() + "/invitations")
                    .header("Authorization", "Bearer " + ctx.accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("username", "missing_user", "role", "EDITOR"))));
        }

        mockMvc.perform(post("/api/workspaces/" + ctx.workspaceId() + "/invitations")
                .header("Authorization", "Bearer " + ctx.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("username", "missing_user", "role", "EDITOR"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void rateLimit_voiceUploadTooManyRequestsReturns429() throws Exception {
        AuthContext ctx = authContext("rl_voice");
        MockMultipartFile file = new MockMultipartFile("file", "voice.webm", "audio/webm", new byte[] {1});
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(multipart("/api/voice-records/" + UUID.randomUUID() + "/audio")
                    .file(file)
                    .header("Authorization", "Bearer " + ctx.accessToken()));
        }

        mockMvc.perform(multipart("/api/voice-records/" + UUID.randomUUID() + "/audio")
                .file(file)
                .header("Authorization", "Bearer " + ctx.accessToken()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void rateLimit_transactionExportTooManyRequestsReturns429() throws Exception {
        AuthContext ctx = authContext("rl_export");
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/workspaces/" + ctx.workspaceId() + "/transactions/export.csv")
                    .header("Authorization", "Bearer " + ctx.accessToken()));
        }

        mockMvc.perform(get("/api/workspaces/" + ctx.workspaceId() + "/transactions/export.csv")
                .header("Authorization", "Bearer " + ctx.accessToken()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void errorResponse_sqlErrorDoesNotExposeDbUrl() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(clock);
        var response = handler.handleAllExceptions(new RuntimeException("jdbc:postgresql://host/db password=secret"));
        assertThat(response.getBody().getMessage()).doesNotContain("jdbc:postgresql", "secret");
        assertThat(response.getBody().getCode()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    void logRedaction_redactsSensitiveValues() {
        String raw = "Authorization: Bearer abc.def.ghi DATABASE_URL=postgresql://u:p@h/db CLOUDINARY_URL=cloudinary://k:s@c api_secret=abc storagePublicId=voice/raw";
        String redacted = LogRedactor.redact(raw);
        assertThat(redacted).doesNotContain("abc.def.ghi", "postgresql://", "cloudinary://", "api_secret=abc", "voice/raw");
        assertThat(redacted).contains("[REDACTED]");
    }

    @Test
    void securityHeaders_presentOnApiResponse() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    void cors_doesNotAllowWildcardWithCredentialsInStaging() {
        SecurityConfig config = new SecurityConfig(jwtAuthenticationFilter, rateLimitFilter, null, "*,https://staging.example.test");
        UrlBasedCorsConfigurationSource source = (UrlBasedCorsConfigurationSource) config.corsConfigurationSource();
        CorsConfiguration cors = source.getCorsConfiguration(new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/me"));
        assertThat(cors.getAllowCredentials()).isTrue();
        assertThat(cors.getAllowedOrigins()).containsExactly("https://staging.example.test");
    }

    private AuthContext authContext(String username) {
        UserResponse user = authService.register(registerRequest(username));
        TokenResponse token = authService.login(loginRequest(username));
        Workspace workspace = workspaceRepository.findAllByUserId(user.getId()).get(0);
        return new AuthContext(token.getAccessToken(), workspace.getId());
    }

    private RegisterRequest registerRequest(String username) {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(username);
        req.setEmail(username + "@example.com");
        req.setPassword("StrongPassword123");
        req.setFullName("Security Test");
        return req;
    }

    private LoginRequest loginRequest(String username) {
        LoginRequest req = new LoginRequest();
        req.setIdentifier(username);
        req.setPassword("StrongPassword123");
        return req;
    }

    private record AuthContext(String accessToken, UUID workspaceId) {
    }
}
