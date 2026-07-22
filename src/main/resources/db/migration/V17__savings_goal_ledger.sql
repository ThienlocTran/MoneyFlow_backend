CREATE TABLE savings_goal_ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    savings_goal_id UUID NOT NULL REFERENCES savings_goals(id),
    entry_type VARCHAR(20) NOT NULL
        CHECK (entry_type IN ('CONTRIBUTION', 'RELEASE')),
    amount_delta NUMERIC(19,2) NOT NULL CHECK (amount_delta <> 0),
    note VARCHAR(500),
    actor_user_id UUID NOT NULL REFERENCES users(id),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (
        (entry_type = 'CONTRIBUTION' AND amount_delta > 0)
        OR (entry_type = 'RELEASE' AND amount_delta < 0)
    )
);

CREATE INDEX idx_savings_goal_ledger_goal_occurred
    ON savings_goal_ledger_entries (workspace_id, savings_goal_id, occurred_at DESC, created_at DESC);

CREATE INDEX idx_savings_goal_ledger_actor
    ON savings_goal_ledger_entries (workspace_id, actor_user_id);
