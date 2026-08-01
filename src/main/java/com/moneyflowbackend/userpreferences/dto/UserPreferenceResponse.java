package com.moneyflowbackend.userpreferences.dto;

public record UserPreferenceResponse(
        String locale,
        boolean onboardingWelcomeSeen,
        boolean onboardingMainTourCompleted,
        boolean onboardingMainTourSkipped,
        String onboardingVersion) {
}
