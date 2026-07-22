CREATE TABLE planning_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL UNIQUE REFERENCES workspaces(id) ON DELETE CASCADE,
    default_horizon VARCHAR(20) NOT NULL DEFAULT 'CURRENT_MONTH'
        CHECK (default_horizon IN ('CURRENT_MONTH', 'CUSTOM')),
    custom_from DATE,
    custom_to DATE,
    use_included_wallets BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (
        default_horizon <> 'CUSTOM'
        OR (custom_from IS NOT NULL AND custom_to IS NOT NULL AND custom_to >= custom_from)
    )
);

CREATE TABLE planning_preference_wallet_ids (
    planning_preference_id UUID NOT NULL REFERENCES planning_preferences(id) ON DELETE CASCADE,
    wallet_id UUID NOT NULL REFERENCES wallets(id) ON DELETE CASCADE,
    PRIMARY KEY (planning_preference_id, wallet_id)
);

CREATE INDEX idx_planning_preference_wallet_ids_wallet
    ON planning_preference_wallet_ids (wallet_id);
