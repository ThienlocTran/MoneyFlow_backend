package com.moneyflowbackend.userpreferences.service;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.userpreferences.dto.UserPreferenceRequest;
import com.moneyflowbackend.userpreferences.dto.UserPreferenceResponse;
import com.moneyflowbackend.userpreferences.model.UserPreference;
import com.moneyflowbackend.userpreferences.repository.UserPreferenceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
public class UserPreferenceService {
    private static final Set<String> ALLOWED_LOCALES = Set.of("vi-VN", "en-US");

    private final UserPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;

    public UserPreferenceService(UserPreferenceRepository preferenceRepository, UserRepository userRepository) {
        this.preferenceRepository = preferenceRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public UserPreferenceResponse get(UUID userId) {
        return map(getOrCreate(userId));
    }

    @Transactional
    public UserPreferenceResponse patch(UUID userId, UserPreferenceRequest request) {
        UserPreference preference = getOrCreate(userId);
        if (request == null) {
            return map(preference);
        }
        if (request.locale() != null) {
            preference.setLocale(validLocale(request.locale()));
        }
        if (request.onboardingWelcomeSeen() != null) {
            preference.setOnboardingWelcomeSeen(request.onboardingWelcomeSeen());
        }
        if (request.onboardingMainTourCompleted() != null) {
            preference.setOnboardingMainTourCompleted(request.onboardingMainTourCompleted());
        }
        if (request.onboardingMainTourSkipped() != null) {
            preference.setOnboardingMainTourSkipped(request.onboardingMainTourSkipped());
        }
        if (request.onboardingVersion() != null) {
            preference.setOnboardingVersion(request.onboardingVersion().isBlank()
                    ? UserPreference.DEFAULT_ONBOARDING_VERSION
                    : request.onboardingVersion().trim());
        }
        return map(preferenceRepository.saveAndFlush(preference));
    }

    private UserPreference getOrCreate(UUID userId) {
        return preferenceRepository.findById(userId)
                .orElseGet(() -> preferenceRepository.saveAndFlush(UserPreference.builder()
                        .user(user(userId))
                        .build()));
    }

    private User user(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));
    }

    private String validLocale(String locale) {
        String normalized = locale.trim();
        if (!ALLOWED_LOCALES.contains(normalized)) {
            throw new BusinessException("INVALID_LOCALE", "Locale must be vi-VN or en-US");
        }
        return normalized;
    }

    private UserPreferenceResponse map(UserPreference preference) {
        return new UserPreferenceResponse(
                preference.getLocale(),
                preference.isOnboardingWelcomeSeen(),
                preference.isOnboardingMainTourCompleted(),
                preference.isOnboardingMainTourSkipped(),
                preference.getOnboardingVersion());
    }
}
