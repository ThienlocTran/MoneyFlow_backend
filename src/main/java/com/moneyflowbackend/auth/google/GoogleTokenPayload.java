package com.moneyflowbackend.auth.google;

public record GoogleTokenPayload(
        String subject,
        String email,
        boolean emailVerified,
        String name,
        String pictureUrl) {
}
