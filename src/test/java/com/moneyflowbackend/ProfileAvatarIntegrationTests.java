package com.moneyflowbackend;

import com.moneyflowbackend.auth.dto.LoginRequest;
import com.moneyflowbackend.auth.dto.RegisterRequest;
import com.moneyflowbackend.auth.dto.TokenResponse;
import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.auth.service.AuthService;
import com.moneyflowbackend.profile.avatar.AvatarStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.MediaType;

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
                .andExpect(jsonPath("$.data.avatarUrl").value("https://cdn.example/users/" + token.getUser().getId() + "/avatar.png"))
                .andExpect(jsonPath("$.data.email").value("avatar_ok@example.com"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.apiSecret").doesNotExist())
                .andExpect(jsonPath("$.data.secret").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        User user = userRepository.findById(token.getUser().getId()).orElseThrow();
        assertThat(user.getAvatarUrl()).isEqualTo("https://cdn.example/users/" + token.getUser().getId() + "/avatar.png");
        assertThat(avatarStorageService.lastObjectKey).isEqualTo("users/" + token.getUser().getId() + "/avatar");
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
                .andExpect(jsonPath("$.data.avatarUrl").value("https://cdn.example/users/" + token.getUser().getId() + "/avatar.png"));
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
                .andExpect(jsonPath("$.code").value("INVALID_AVATAR_FILE_TYPE"))
                .andExpect(jsonPath("$.message").value("Ảnh đại diện phải là JPEG, PNG hoặc WebP."));
    }

    @Test
    void missingFileReturnsBadRequest() throws Exception {
        TokenResponse token = createUser("avatar_missing");

        mockMvc.perform(multipart("/api/me/avatar")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AVATAR_FILE_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Vui lòng chọn ảnh đại diện."));
    }

    @Test
    void tooLargeReturnsBadRequest() throws Exception {
        TokenResponse token = createUser("avatar_large");
        MockMultipartFile file = new MockMultipartFile("file", "avatar.webp", "image/webp", new byte[2097153]);

        mockMvc.perform(multipart("/api/me/avatar")
                        .file(file)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AVATAR_FILE_TOO_LARGE"))
                .andExpect(jsonPath("$.message").value("Ảnh đại diện không được vượt quá 2MB."));
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

        void reset() {
            lastObjectKey = null;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public String upload(String objectKey, MultipartFile file) {
            lastObjectKey = objectKey;
            return "https://cdn.example/" + objectKey + ".png";
        }
    }
}
