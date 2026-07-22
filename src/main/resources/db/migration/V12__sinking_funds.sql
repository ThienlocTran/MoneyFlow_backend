CREATE TABLE sinking_funds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    name VARCHAR(160) NOT NULL,
    description VARCHAR(500),
    target_amount NUMERIC(19,2) CHECK (target_amount IS NULL OR target_amount > 0),
    target_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'PAUSED', 'COMPLETED', 'ARCHIVED')),
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (BTRIM(name) <> '')
);

CREATE INDEX idx_sinking_funds_workspace_status
    ON sinking_funds (workspace_id, status);

CREATE INDEX idx_sinking_funds_workspace_status_target_date
    ON sinking_funds (workspace_id, status, target_date);

CREATE UNIQUE INDEX uq_sinking_funds_workspace_open_name
    ON sinking_funds (workspace_id, LOWER(BTRIM(name)))
    WHERE status <> 'ARCHIVED';

CREATE TABLE sinking_fund_allocations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    sinking_fund_id UUID NOT NULL REFERENCES sinking_funds(id),
    allocation_type VARCHAR(20) NOT NULL
        CHECK (allocation_type IN ('ALLOCATE', 'RELEASE', 'ADJUST')),
    amount_delta NUMERIC(19,2) NOT NULL CHECK (amount_delta <> 0),
    note VARCHAR(500),
    actor_user_id UUID NOT NULL REFERENCES users(id),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_sinking_fund_allocations_fund_occurred
    ON sinking_fund_allocations (workspace_id, sinking_fund_id, occurred_at DESC, created_at DESC);

CREATE INDEX idx_sinking_fund_allocations_actor
    ON sinking_fund_allocations (workspace_id, actor_user_id);
