package com.moneyflowbackend.auth.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.moneyflowbackend.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GoogleIdTokenVerifierService implements GoogleTokenVerifier {

    private final GoogleIdTokenVerifier verifier;
    private final boolean configured;

    public GoogleIdTokenVerifierService(
            @Value("${GOOGLE_CLIENT_ID:${GOOGLE_ALLOWED_AUDIENCE:}}") String audience) {
        String value = audience == null ? "" : audience.trim();
        this.configured = !value.isBlank();
        this.verifier = this.configured
                ? new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                        .setAudience(List.of(value))
                        .build()
                : null;
    }

    @Override
    public GoogleTokenPayload verify(String credential) {
        if (!configured) {
            throw new BusinessException("GOOGLE_LOGIN_NOT_CONFIGURED", "Không thể đăng nhập bằng Google lúc này.", HttpStatus.SERVICE_UNAVAILABLE);
        }
        try {
            GoogleIdToken idToken = verifier.verify(credential);
            if (idToken == null) {
                throw new BusinessException("INVALID_GOOGLE_CREDENTIAL", "Google credential không hợp lệ", HttpStatus.UNAUTHORIZED);
            }
            GoogleIdToken.Payload payload = idToken.getPayload();
            Object name = payload.get("name");
            Object picture = payload.get("picture");
            return new GoogleTokenPayload(
                    payload.getSubject(),
                    payload.getEmail(),
                    Boolean.TRUE.equals(payload.getEmailVerified()),
                    name instanceof String value ? value : null,
                    picture instanceof String value ? value : null);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("INVALID_GOOGLE_CREDENTIAL", "Google credential không hợp lệ", HttpStatus.UNAUTHORIZED);
        }
    }
}
