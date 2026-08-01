package com.moneyflowbackend;

import com.moneyflowbackend.auth.dto.LoginRequest;
import com.moneyflowbackend.auth.dto.RegisterRequest;
import com.moneyflowbackend.auth.dto.TokenResponse;
import com.moneyflowbackend.auth.service.AuthService;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "MONEYFLOW_RECEIPT_OCR_PROVIDER=mock")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReceiptOcrMockIntegrationTests {
    @Autowired AuthService authService;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired MockMvc mockMvc;

    @Test
    void imagesOnlyWithMockOcrExtractsTextAndParsesDraft() throws Exception {
        TokenResponse token = registerAndLogin("receipt_ocr_mock");
        UUID workspaceId = workspaceRepository.findAllByUserId(token.getUser().getId()).getFirst().getId();

        mockMvc.perform(multipart("/api/workspaces/{workspaceId}/receipt-review/parse-with-images", workspaceId)
                        .file(new MockMultipartFile("images", "receipt.jpg", "image/jpeg", new byte[] {1, 2, 3}))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.data.source").value("PHOTO_OCR"))
                .andExpect(jsonPath("$.data.imageCount").value(1))
                .andExpect(jsonPath("$.data.ocr.provider").value("MOCK"))
                .andExpect(jsonPath("$.data.ocr.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.candidate.type").value("EXPENSE"))
                .andExpect(jsonPath("$.data.candidate.amount").value(40000));
    }

    private TokenResponse registerAndLogin(String prefix) {
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
        return authService.login(login);
    }

    private String bearer(TokenResponse token) {
        return "Bearer " + token.getAccessToken();
    }
}
