CREATE TABLE income_sources (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    name VARCHAR(160) NOT NULL,
    type VARCHAR(20)
        CHECK (type IN ('SALARY', 'FREELANCE', 'BUSINESS', 'GIG_PLATFORM', 'INVESTMENT', 'RENTAL', 'OTHER')),
    description VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (BTRIM(name) <> '')
);

CREATE INDEX idx_income_sources_workspace_status
    ON income_sources (workspace_id, status);

CREATE INDEX idx_income_sources_workspace_status_name
    ON income_sources (workspace_id, status, name);

CREATE UNIQUE INDEX uq_income_sources_workspace_active_name
    ON income_sources (workspace_id, LOWER(BTRIM(name)))
    WHERE status = 'ACTIVE';
