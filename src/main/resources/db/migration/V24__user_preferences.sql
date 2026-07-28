CREATE TABLE user_preferences (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    locale VARCHAR(16) NOT NULL DEFAULT 'vi-VN'
        CHECK (locale IN ('vi-VN', 'en-US')),
    onboarding_welcome_seen BOOLEAN NOT NULL DEFAULT FALSE,
    onboarding_main_tour_completed BOOLEAN NOT NULL DEFAULT FALSE,
    onboarding_main_tour_skipped BOOLEAN NOT NULL DEFAULT FALSE,
    onboarding_version VARCHAR(32) NOT NULL DEFAULT '2026-07',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
