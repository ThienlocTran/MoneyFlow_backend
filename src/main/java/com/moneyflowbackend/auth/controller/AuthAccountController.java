package com.moneyflowbackend.auth.controller;

import com.moneyflowbackend.auth.dto.AuthAccountStatusResponse;
import com.moneyflowbackend.auth.model.AuthProvider;
import com.moneyflowbackend.auth.repository.AuthAccountRepository;
import com.moneyflowbackend.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/me/auth-accounts")
public class AuthAccountController {
    private final AuthAccountRepository authAccountRepository;

    public AuthAccountController(AuthAccountRepository authAccountRepository) {
        this.authAccountRepository = authAccountRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AuthAccountStatusResponse>> status() {
        UUID userId = UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
        var providers = authAccountRepository.findAllByUserId(userId).stream()
                .map(account -> account.getProvider())
                .filter(Objects::nonNull)
                .map(Enum::name)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        return ResponseEntity.ok(ApiResponse.ok("Auth accounts loaded", AuthAccountStatusResponse.builder()
                .googleLinked(providers.contains(AuthProvider.GOOGLE.name()))
                .providers(providers)
                .build()));
    }
}
