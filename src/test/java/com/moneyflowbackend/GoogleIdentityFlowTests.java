package com.moneyflowbackend;

import com.moneyflowbackend.auth.dto.GoogleLoginRequest;
import com.moneyflowbackend.auth.dto.LoginRequest;
import com.moneyflowbackend.auth.dto.RegisterRequest;
import com.moneyflowbackend.auth.dto.TokenResponse;
import com.moneyflowbackend.auth.dto.UsernameUpdateRequest;
import com.moneyflowbackend.auth.google.GoogleTokenPayload;
import com.moneyflowbackend.auth.google.GoogleTokenVerifier;
import com.moneyflowbackend.auth.model.AuthProvider;
import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.model.UserStatus;
import com.moneyflowbackend.auth.repository.AuthAccountRepository;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.auth.service.AuthService;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.workspace.dto.WorkspaceInvitationRequest;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import com.moneyflowbackend.workspace.service.WorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GoogleIdentityFlowTests {

    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired AuthAccountRepository authAccountRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceService workspaceService;
    @Autowired MockMvc mockMvc;
    @Autowired FakeGoogleTokenVerifier googleVerifier;

    @BeforeEach
    void resetVerifier() {
        googleVerifier.reset();
    }

    @Test
    void localLogin_stillWorks() {
        authService.register(registerRequest("local_ok", "local_ok@example.com"));

        TokenResponse token = authService.login(loginRequest("local_ok"));

        assertThat(token.getAccessToken()).isNotBlank();
        assertThat(token.getUser().getUsername()).isEqualTo("local_ok");
    }

    @Test
    void register_createsPersonalWorkspace() {
        var user = authService.register(registerRequest("workspace_ok", "workspace_ok@example.com"));

        assertThat(workspaceRepository.findAllByUserId(user.getId())).hasSize(1);
    }

    @Test
    void googleLogin_newVerifiedEmailCreatesUserAndWorkspace() {
        googleVerifier.add("new-token", payload("google-sub-1", "new-google@example.com", true));

        TokenResponse token = authService.googleLogin(googleRequest("new-token"));

        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("new-google@example.com").orElseThrow();
        assertThat(token.getUser().getId()).isEqualTo(user.getId());
        assertThat(token.getUser().getUsername()).isEqualTo("new.google");
        assertThat(workspaceRepository.findAllByUserId(user.getId())).hasSize(1);
        assertThat(authAccountRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-sub-1")).isPresent();
    }

    @Test
    void googleLogin_existingEmailLinksAccountNoDuplicateUser() {
        authService.register(registerRequest("linked_local", "linkme@example.com"));
        googleVerifier.add("link-token", payload("google-sub-2", "linkme@example.com", true));

        TokenResponse token = authService.googleLogin(googleRequest("link-token"));

        assertThat(userRepository.findAll()).hasSize(1);
        assertThat(token.getUser().getUsername()).isEqualTo("linked_local");
        assertThat(authAccountRepository.existsByUserIdAndProvider(token.getUser().getId(), AuthProvider.GOOGLE)).isTrue();
    }

    @Test
    void googleLogin_existingLinkedAccountLogsIn() {
        googleVerifier.add("first-token", payload("google-sub-3", "linked@example.com", true));
        TokenResponse first = authService.googleLogin(googleRequest("first-token"));
        googleVerifier.add("second-token", payload("google-sub-3", "linked@example.com", true));

        TokenResponse second = authService.googleLogin(googleRequest("second-token"));

        assertThat(second.getUser().getId()).isEqualTo(first.getUser().getId());
        assertThat(userRepository.findAll()).hasSize(1);
    }

    @Test
    void googleLogin_rejectsUnverifiedEmail() {
        googleVerifier.add("unverified-token", payload("google-sub-4", "not-verified@example.com", false));

        assertThatThrownBy(() -> authService.googleLogin(googleRequest("unverified-token")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("GOOGLE_EMAIL_NOT_VERIFIED");
    }

    @Test
    void googleLogin_rejectsLockedUser() {
        authService.register(registerRequest("locked_google", "locked-google@example.com"));
        User locked = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("locked-google@example.com").orElseThrow();
        locked.setStatus(UserStatus.LOCKED);
        userRepository.save(locked);
        googleVerifier.add("locked-token", payload("google-sub-5", "locked-google@example.com", true));

        assertThatThrownBy(() -> authService.googleLogin(googleRequest("locked-token")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("ACCOUNT_LOCKED");
    }

    @Test
    void googleLogin_requiresOrAssignsUniqueUsername() {
        authService.register(registerRequest("dupe", "dupe-local@example.com"));
        googleVerifier.add("dupe-token", payload("google-sub-6", "dupe@example.com", true));

        TokenResponse token = authService.googleLogin(googleRequest("dupe-token"));

        assertThat(token.getUser().getUsername()).isEqualTo("dupe1");
    }

    @Test
    void usernameUpdate_rejectsDuplicate() {
        authService.register(registerRequest("taken_name", "taken@example.com"));
        var user = authService.register(registerRequest("rename_me", "rename@example.com"));
        UsernameUpdateRequest req = new UsernameUpdateRequest();
        req.setUsername("taken_name");

        assertThatThrownBy(() -> authService.updateUsername(user.getId(), req))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("USERNAME_ALREADY_EXISTS");
    }

    @Test
    void usernameSearch_returnsSafeFieldsOnlyNoEmail() throws Exception {
        authService.register(registerRequest("owner_safe", "owner_safe@example.com"));
        authService.register(registerRequest("safe_target", "safe_target@example.com"));
        TokenResponse token = authService.login(loginRequest("owner_safe"));

        mockMvc.perform(get("/api/users/search")
                        .header("Authorization", "Bearer " + token.getAccessToken())
                        .param("username", "safe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].username").value("safe_target"))
                .andExpect(jsonPath("$.data[0].email").doesNotExist());
    }

    @Test
    void invitation_ownerCanInviteByUsernameAfterGoogleSignup() {
        var owner = authService.register(registerRequest("invite_owner", "invite_owner@example.com"));
        googleVerifier.add("invitee-token", payload("google-sub-7", "invitee@example.com", true));
        TokenResponse invitee = authService.googleLogin(googleRequest("invitee-token"));
        var workspace = workspaceRepository.findAllByUserId(owner.getId()).get(0);
        WorkspaceInvitationRequest req = new WorkspaceInvitationRequest();
        req.setUsername(invitee.getUser().getUsername());
        req.setRole("EDITOR");

        var invitation = workspaceService.invite(workspace.getId(), req, owner.getId());

        assertThat(invitation.getInvitedUsername()).isEqualTo(invitee.getUser().getUsername());
    }

    private GoogleLoginRequest googleRequest(String credential) {
        GoogleLoginRequest req = new GoogleLoginRequest();
        req.setCredential(credential);
        return req;
    }

    private GoogleTokenPayload payload(String subject, String email, boolean verified) {
        return new GoogleTokenPayload(subject, email, verified, "Google User", "https://example.com/avatar.png");
    }

    private RegisterRequest registerRequest(String username, String email) {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(username);
        req.setEmail(email);
        req.setPassword("StrongPassword123");
        req.setFullName("Test User");
        return req;
    }

    private LoginRequest loginRequest(String identifier) {
        LoginRequest req = new LoginRequest();
        req.setIdentifier(identifier);
        req.setPassword("StrongPassword123");
        return req;
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
                throw new BusinessException("INVALID_GOOGLE_CREDENTIAL", "Google credential không hợp lệ");
            }
            return payload;
        }
    }
}
