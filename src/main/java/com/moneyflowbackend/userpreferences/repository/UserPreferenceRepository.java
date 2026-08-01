package com.moneyflowbackend.userpreferences.repository;

import com.moneyflowbackend.userpreferences.model.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, UUID> {
}
