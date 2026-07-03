package com.moneyflowbackend.auth.google;

public interface GoogleTokenVerifier {
    GoogleTokenPayload verify(String credential);
}
