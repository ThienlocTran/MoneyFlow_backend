package com.moneyflowbackend;

import com.moneyflowbackend.auth.dto.LoginRequest;
import com.moneyflowbackend.auth.dto.RegisterRequest;
import com.moneyflowbackend.auth.dto.TokenResponse;
import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.auth.service.AuthService;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.profile.avatar.AvatarStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProfileAvatarIntegrationTests {
    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired MockMvc mockMvc;
    @Autowired FakeAvatarStorageService avatarStorageService;

    @BeforeEach
    void resetStorage() {
        avatarStorageService.reset();
    }

    @Test
    void uploadValidFileReturnsProfileAndUpdatesAvatarUrl() throws Exception {
        TokenResponse token = createUser("avatar_ok");
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[] {1, 2, 3});

        String body = mockMvc.perform(multipart("/api/me/avatar")
                        .file(file)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value(startsWith("https://cdn.example/avatars/" + token.getUser().getId() + "/")))
                .andExpect(jsonPath("$.data.email").value("avatar_ok@example.com"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.apiSecret").doesNotExist())
                .andExpect(jsonPath("$.data.secret").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        User user = userRepository.findById(token.getUser().getId()).orElseThrow();
        assertThat(user.getAvatarUrl()).startsWith("https://cdn.example/avatars/" + token.getUser().getId() + "/");
        assertThat(avatarStorageService.lastObjectKey).startsWith("avatars/" + token.getUser().getId() + "/");
        assertThat(body).doesNotContain("secret", "apiKey", "api_secret", "cloudinary://");
    }

    @Test
    void meReturnsPersistedAvatarUrlAfterUpload() throws Exception {
        TokenResponse token = createUser("avatar_reload");
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[] {1});

        mockMvc.perform(multipart("/api/me/avatar")
                        .file(file)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/me")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value(startsWith("https://cdn.example/avatars/" + token.getUser().getId() + "/")));
    }

    @Test
    void replacementDeletesPreviousAvatarAfterNewUploadSucceeds() throws Exception {
        TokenResponse token = createUser("avatar_replace");
        User user = userRepository.findById(token.getUser().getId()).orElseThrow();
        user.setAvatarUrl("https://cdn.example/old-avatar.png");
        userRepository.save(user);
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[] {1});

        mockMvc.perform(multipart("/api/me/avatar")
                        .file(file)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value(startsWith("https://cdn.example/avatars/" + token.getUser().getId() + "/")));

        assertThat(avatarStorageService.deletedAvatarUrl).isEqualTo("https://cdn.example/old-avatar.png");
    }

    @Test
    void replacementKeepsNewAvatarWhenOldCleanupFails() throws Exception {
        TokenResponse token = createUser("avatar_replace_cleanup_fail");
        User user = userRepository.findById(token.getUser().getId()).orElseThrow();
        user.setAvatarUrl("https://cdn.example/old-avatar.png");
        userRepository.save(user);
        avatarStorageService.failDelete = true;
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[] {1});

        mockMvc.perform(multipart("/api/me/avatar")
                        .file(file)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        assertThat(userRepository.findById(token.getUser().getId()).orElseThrow().getAvatarUrl())
                .startsWith("https://cdn.example/avatars/" + token.getUser().getId() + "/");
    }

    @Test
    void deleteAvatarClearsProfileAndDeletesStoredAvatar() throws Exception {
        TokenResponse token = createUser("avatar_delete");
        User user = userRepository.findById(token.getUser().getId()).orElseThrow();
        user.setAvatarUrl("https://cdn.example/delete-me.png");
        userRepository.save(user);

        mockMvc.perform(delete("/api/me/avatar")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").doesNotExist());

        assertThat(userRepository.findById(token.getUser().getId()).orElseThrow().getAvatarUrl()).isNull();
        assertThat(avatarStorageService.deletedAvatarUrl).isEqualTo("https://cdn.example/delete-me.png");
    }

    @Test
    void updateProfileDoesNotClearAvatarUrlWhenAvatarMissingOrBlank() throws Exception {
        TokenResponse token = createUser("avatar_profile_update");
        User user = userRepository.findById(token.getUser().getId()).orElseThrow();
        user.setAvatarUrl("https://cdn.example/persisted.png");
        userRepository.save(user);

        mockMvc.perform(put("/api/me")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Renamed User\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Renamed User"))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://cdn.example/persisted.png"));

        mockMvc.perform(put("/api/me")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Renamed Again\",\"avatarUrl\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value("https://cdn.example/persisted.png"));
    }

    @Test
    void invalidContentTypeReturnsBadRequest() throws Exception {
        TokenResponse token = createUser("avatar_type");
        MockMultipartFile file = new MockMultipartFile("file", "avatar.gif", "image/gif", new byte[] {1});

        mockMvc.perform(multipart("/api/me/avatar")
                        .file(file)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AVATAR_FILE_TYPE"));
    }

    @Test
    void missingFileReturnsBadRequest() throws Exception {
        TokenResponse token = createUser("avatar_missing");

        mockMvc.perform(multipart("/api/me/avatar")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AVATAR_FILE_REQUIRED"));
    }

    @Test
    void tooLargeReturnsBadRequest() throws Exception {
        TokenResponse token = createUser("avatar_large");
        MockMultipartFile file = new MockMultipartFile("file", "avatar.webp", "image/webp", new byte[2097153]);

        mockMvc.perform(multipart("/api/me/avatar")
                        .file(file)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AVATAR_FILE_TOO_LARGE"));
    }

    @Test
    void missingStorageConfigReturnsClearServiceUnavailable() throws Exception {
        avatarStorageService.enabled = false;
        TokenResponse token = createUser("avatar_storage_missing");
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[] {1});

        mockMvc.perform(multipart("/api/me/avatar")
                        .file(file)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("STORAGE_NOT_CONFIGURED"));
    }

    @Test
    void storageProviderFailureReturnsSafeMessage() throws Exception {
        avatarStorageService.failUpload = true;
        TokenResponse token = createUser("avatar_storage_fail");
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[] {1});

        mockMvc.perform(multipart("/api/me/avatar")
                        .file(file)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("AVATAR_STORAGE_FAILED"));
    }

    @Test
    void unauthenticatedReturnsUnauthorized() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", new byte[] {1});

        mockMvc.perform(multipart("/api/me/avatar").file(file))
                .andExpect(status().isUnauthorized());
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
        req.setFullName("Avatar User");
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

    @TestConfiguration
    static class AvatarTestConfig {
        @Bean
        @Primary
        FakeAvatarStorageService fakeAvatarStorageService() {
            return new FakeAvatarStorageService();
        }
    }

    static class FakeAvatarStorageService implements AvatarStorageService {
        private String lastObjectKey;
        private String deletedAvatarUrl;
        private boolean enabled = true;
        private boolean failUpload;
        private boolean failDelete;

        void reset() {
            lastObjectKey = null;
            deletedAvatarUrl = null;
            enabled = true;
            failUpload = false;
            failDelete = false;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public String upload(String objectKey, MultipartFile file) {
            if (failUpload) {
                throw new BusinessException("AVATAR_STORAGE_FAILED", "provider secret detail", HttpStatus.BAD_GATEWAY);
            }
            lastObjectKey = objectKey;
            return "https://cdn.example/" + objectKey + ".png";
        }

        @Override
        public void delete(String avatarUrl) {
            if (failDelete) {
                throw new BusinessException("AVATAR_STORAGE_FAILED", "provider secret detail", HttpStatus.BAD_GATEWAY);
            }
            deletedAvatarUrl = avatarUrl;
        }
    }
}
