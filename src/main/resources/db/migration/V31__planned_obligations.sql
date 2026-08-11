CREATE TABLE planned_obligations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(120) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    due_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED'
        CHECK (status IN ('PLANNED', 'PAID', 'CANCELLED')),
    priority VARCHAR(20) NOT NULL DEFAULT 'REQUIRED'
        CHECK (priority IN ('REQUIRED', 'IMPORTANT', 'OPTIONAL')),
    recurrence_type VARCHAR(20) NOT NULL DEFAULT 'NONE'
        CHECK (recurrence_type IN ('NONE', 'WEEKLY', 'MONTHLY', 'YEARLY')),
    wallet_id UUID REFERENCES wallets(id) ON DELETE SET NULL,
    category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    linked_transaction_id UUID REFERENCES transactions(id) ON DELETE SET NULL,
    note TEXT,
    cancel_reason VARCHAR(500),
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (trim(name) <> ''),
    CHECK (amount > 0)
);

CREATE INDEX idx_planned_obligations_workspace_due
    ON planned_obligations (workspace_id, due_date, status)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_planned_obligations_workspace_status
    ON planned_obligations (workspace_id, status)
    WHERE deleted_at IS NULL;
