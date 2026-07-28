package com.moneyflowbackend.userpreferences.model;

import com.moneyflowbackend.auth.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPreference {
    public static final String DEFAULT_LOCALE = "vi-VN";
    public static final String DEFAULT_ONBOARDING_VERSION = "2026-07";

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 16)
    @Builder.Default
    private String locale = DEFAULT_LOCALE;

    @Column(name = "onboarding_welcome_seen", nullable = false)
    @Builder.Default
    private boolean onboardingWelcomeSeen = false;

    @Column(name = "onboarding_main_tour_completed", nullable = false)
    @Builder.Default
    private boolean onboardingMainTourCompleted = false;

    @Column(name = "onboarding_main_tour_skipped", nullable = false)
    @Builder.Default
    private boolean onboardingMainTourSkipped = false;

    @Column(name = "onboarding_version", nullable = false, length = 32)
    @Builder.Default
    private String onboardingVersion = DEFAULT_ONBOARDING_VERSION;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
