CREATE TABLE savings_goals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    name VARCHAR(160) NOT NULL,
    description VARCHAR(500),
    target_amount NUMERIC(19,2) NOT NULL CHECK (target_amount > 0),
    target_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'PAUSED', 'COMPLETED', 'ARCHIVED')),
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (BTRIM(name) <> '')
);

CREATE INDEX idx_savings_goals_workspace_status
    ON savings_goals (workspace_id, status);

CREATE INDEX idx_savings_goals_workspace_status_target_date
    ON savings_goals (workspace_id, status, target_date);

CREATE UNIQUE INDEX uq_savings_goals_workspace_open_name
    ON savings_goals (workspace_id, LOWER(BTRIM(name)))
    WHERE status <> 'ARCHIVED';
