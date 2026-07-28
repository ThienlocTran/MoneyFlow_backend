package com.moneyflowbackend.userpreferences.dto;

public record UserPreferenceRequest(
        String locale,
        Boolean onboardingWelcomeSeen,
        Boolean onboardingMainTourCompleted,
        Boolean onboardingMainTourSkipped,
        String onboardingVersion) {
}
