package com.moneyflowbackend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneyflowbackend.auth.dto.LoginRequest;
import com.moneyflowbackend.auth.dto.RefreshRequest;
import com.moneyflowbackend.auth.dto.RegisterRequest;
import com.moneyflowbackend.auth.dto.TokenResponse;
import com.moneyflowbackend.auth.dto.UserResponse;
import com.moneyflowbackend.auth.model.RefreshToken;
import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.model.UserStatus;
import com.moneyflowbackend.auth.repository.AuthAccountRepository;
import com.moneyflowbackend.auth.repository.RefreshTokenRepository;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.auth.service.AuthService;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.jar.repository.JarRepository;
import com.moneyflowbackend.wallet.repository.WalletRepository;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspacePersonRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MoneyflowBackendApplicationTests {

    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired AuthAccountRepository authAccountRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspacePersonRepository workspacePersonRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired JarRepository jarRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void contextLoads() {
    }

    @Test
    void registerCreatesUserAndDefaultWorkspaceData() {
        UserResponse res = authService.register(registerRequest("register_ok"));

        User user = userRepository.findById(res.getId()).orElseThrow();
        assertThat(user.getPasswordHash()).isNotEqualTo("StrongPassword123");
        assertThat(passwordEncoder.matches("StrongPassword123", user.getPasswordHash())).isTrue();
        assertThat(authAccountRepository.count()).isEqualTo(1);

        Workspace workspace = workspaceRepository.findAllByUserId(user.getId()).get(0);
        assertThat(workspacePersonRepository.count()).isEqualTo(1);
        assertThat(workspaceMemberRepository.existsByWorkspaceIdAndUserIdAndMemberStatus(workspace.getId(), user.getId(), "ACTIVE")).isTrue();
        assertThat(jarRepository.countByWorkspaceId(workspace.getId())).isEqualTo(6);
        assertThat(categoryRepository.countByWorkspaceId(workspace.getId())).isGreaterThan(0);
        assertThat(walletRepository.countByWorkspaceId(workspace.getId())).isEqualTo(1);
        assertThat(walletRepository.findByWorkspaceIdAndIsDefaultTrueAndIsActiveTrue(workspace.getId())).isPresent();
        assertThat(res.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void registerRejectsDuplicateEmailAndUsernameIgnoringCase() {
        authService.register(registerRequest("duplicate_user"));

        RegisterRequest duplicateEmail = registerRequest("another_user");
        duplicateEmail.setEmail("DUPLICATE_USER@example.com");
        assertThatThrownBy(() -> authService.register(duplicateEmail))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("EMAIL_ALREADY_EXISTS");

        RegisterRequest duplicateUsername = registerRequest("DUPLICATE_USER");
        duplicateUsername.setEmail("another@example.com");
        assertThatThrownBy(() -> authService.register(duplicateUsername))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("USERNAME_ALREADY_EXISTS");
    }

    @Test
    void loginWorksWithEmailAndUsernameAndHidesInvalidCredentialDetails() {
        authService.register(registerRequest("login_user"));

        TokenResponse byUsername = authService.login(loginRequest("login_user", "StrongPassword123"));
        TokenResponse byEmail = authService.login(loginRequest("LOGIN_USER@EXAMPLE.COM", "StrongPassword123"));

        assertThat(byUsername.getAccessToken()).isNotBlank();
        assertThat(byUsername.getRefreshToken()).isNotBlank();
        assertThat(byUsername.getTokenType()).isEqualTo("Bearer");
        assertThat(byUsername.getExpiresIn()).isEqualTo(900);
        assertThat(byUsername.getUser().getUsername()).isEqualTo("login_user");
        assertThat(byEmail.getAccessToken()).isNotBlank();

        assertThatThrownBy(() -> authService.login(loginRequest("login_user", "wrongPassword")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("INVALID_CREDENTIALS");
        assertThatThrownBy(() -> authService.login(loginRequest("missing_user", "StrongPassword123")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void lockedAndDeletedUsersCannotLogin() {
        UserResponse locked = authService.register(registerRequest("locked_user"));
        User lockedUser = userRepository.findById(locked.getId()).orElseThrow();
        lockedUser.setStatus(UserStatus.LOCKED);
        userRepository.save(lockedUser);

        assertThatThrownBy(() -> authService.login(loginRequest("locked_user", "StrongPassword123")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("ACCOUNT_LOCKED");

        UserResponse deleted = authService.register(registerRequest("deleted_user"));
        User deletedUser = userRepository.findById(deleted.getId()).orElseThrow();
        deletedUser.setStatus(UserStatus.DELETED);
        userRepository.save(deletedUser);

        assertThatThrownBy(() -> authService.login(loginRequest("deleted_user", "StrongPassword123")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void refreshStoresHashRotatesAndLogoutRevokesToken() {
        authService.register(registerRequest("refresh_user"));
        TokenResponse login = authService.login(loginRequest("refresh_user", "StrongPassword123"));

        RefreshToken stored = refreshTokenRepository.findAll().get(0);
        assertThat(stored.getTokenHash()).isNotEqualTo(login.getRefreshToken());
        assertThat(stored.getTokenHash()).hasSize(64);

        TokenResponse refreshed = authService.refresh(refreshRequest(login.getRefreshToken()));
        assertThat(refreshed.getAccessToken()).isNotBlank();
        assertThat(refreshed.getRefreshToken()).isNotEqualTo(login.getRefreshToken());
        assertThat(refreshTokenRepository.findById(stored.getId()).orElseThrow().getRevokedAt()).isNotNull();

        authService.logout(refreshed.getRefreshToken());
        assertThat(refreshTokenRepository.findAll().stream().filter(t -> t.getRevokedAt() != null).count()).isEqualTo(2);
    }

    @Test
    void refreshRejectsExpiredAndRevokedTokens() {
        authService.register(registerRequest("expired_user"));
        TokenResponse login = authService.login(loginRequest("expired_user", "StrongPassword123"));
        RefreshToken token = refreshTokenRepository.findAll().get(0);

        token.setExpiresAt(Instant.now().minusSeconds(1));
        refreshTokenRepository.save(token);
        assertThatThrownBy(() -> authService.refresh(refreshRequest(login.getRefreshToken())))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("REFRESH_TOKEN_EXPIRED");

        TokenResponse secondLogin = authService.login(loginRequest("expired_user", "StrongPassword123"));
        RefreshToken secondToken = refreshTokenRepository.findAll().stream().filter(t -> t.getRevokedAt() == null && t.getExpiresAt().isAfter(Instant.now())).findFirst().orElseThrow();
        secondToken.setRevokedAt(Instant.now());
        refreshTokenRepository.save(secondToken);
        assertThatThrownBy(() -> authService.refresh(refreshRequest(secondLogin.getRefreshToken())))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("INVALID_REFRESH_TOKEN");
    }

    @Test
    void securityProtectsMeAndAllowsPublicEndpoints() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        String username = "web_user";
        mockMvc.perform(post("/api/public/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "username", username,
                        "email", username + "@example.com",
                        "password", "StrongPassword123",
                        "fullName", "Web User"))))
                .andExpect(status().isOk());

        String loginResponse = mockMvc.perform(post("/api/public/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "identifier", username,
                        "password", "StrongPassword123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode loginJson = objectMapper.readTree(loginResponse);
        String accessToken = loginJson.path("data").path("accessToken").asText();
        String refreshToken = loginJson.path("data").path("refreshToken").asText();

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(post("/api/public/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
    }

    private RegisterRequest registerRequest(String username) {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(username);
        req.setEmail(username + "@example.com");
        req.setPassword("StrongPassword123");
        req.setFullName("Test User");
        return req;
    }

    private LoginRequest loginRequest(String identifier, String password) {
        LoginRequest req = new LoginRequest();
        req.setIdentifier(identifier);
        req.setPassword(password);
        return req;
    }

    private RefreshRequest refreshRequest(String refreshToken) {
        RefreshRequest req = new RefreshRequest();
        req.setRefreshToken(refreshToken);
        return req;
    }
}