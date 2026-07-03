package com.moneyflowbackend.auth.repository;

import com.moneyflowbackend.auth.model.AuthAccount;
import com.moneyflowbackend.auth.model.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface AuthAccountRepository extends JpaRepository<AuthAccount, UUID> {
    Optional<AuthAccount> findByProviderAndProviderSubject(AuthProvider provider, String providerSubject);
    boolean existsByUserIdAndProvider(UUID userId, AuthProvider provider);
}
