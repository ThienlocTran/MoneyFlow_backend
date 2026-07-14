package com.moneyflowbackend.auth.repository;

import com.moneyflowbackend.auth.model.AuthAccount;
import com.moneyflowbackend.auth.model.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthAccountRepository extends JpaRepository<AuthAccount, UUID> {
    Optional<AuthAccount> findByProviderAndProviderSubject(AuthProvider provider, String providerSubject);
    List<AuthAccount> findAllByUserId(UUID userId);
    boolean existsByUserIdAndProvider(UUID userId, AuthProvider provider);
}
