CREATE TABLE emergency_fund_ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    emergency_fund_plan_id UUID NOT NULL REFERENCES emergency_fund_plans(id),
    entry_type VARCHAR(20) NOT NULL
        CHECK (entry_type IN ('ALLOCATE', 'RELEASE')),
    amount_delta NUMERIC(19,2) NOT NULL CHECK (amount_delta <> 0),
    note VARCHAR(500),
    actor_user_id UUID NOT NULL REFERENCES users(id),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (
        (entry_type = 'ALLOCATE' AND amount_delta > 0)
        OR (entry_type = 'RELEASE' AND amount_delta < 0)
    )
);

CREATE INDEX idx_emergency_fund_ledger_plan_occurred
    ON emergency_fund_ledger_entries (workspace_id, emergency_fund_plan_id, occurred_at DESC, created_at DESC);

CREATE INDEX idx_emergency_fund_ledger_actor
    ON emergency_fund_ledger_entries (workspace_id, actor_user_id);
