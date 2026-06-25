package com.moneyflowbackend.auth.repository;

import com.moneyflowbackend.auth.model.AuthAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AuthAccountRepository extends JpaRepository<AuthAccount, UUID> {
}