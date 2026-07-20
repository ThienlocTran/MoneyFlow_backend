CREATE TABLE recurring_obligation_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    name VARCHAR(160) NOT NULL,
    direction VARCHAR(20) NOT NULL
        CHECK (direction IN ('PAYABLE', 'RECEIVABLE')),
    amount_mode VARCHAR(20) NOT NULL
        CHECK (amount_mode IN ('FIXED', 'VARIABLE')),
    default_amount NUMERIC(19,2),
    frequency VARCHAR(20) NOT NULL
        CHECK (frequency IN ('WEEKLY', 'MONTHLY', 'YEARLY')),
    interval_count INTEGER NOT NULL DEFAULT 1 CHECK (interval_count >= 1),
    start_date DATE NOT NULL,
    end_date DATE,
    reminder_days_before INTEGER NOT NULL DEFAULT 0 CHECK (reminder_days_before >= 0),
    default_wallet_id UUID REFERENCES wallets(id),
    default_category_id UUID REFERENCES categories(id),
    note TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'PAUSED', 'ARCHIVED')),
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (BTRIM(name) <> ''),
    CHECK (end_date IS NULL OR end_date >= start_date),
    CHECK (default_amount IS NULL OR default_amount > 0),
    CHECK (amount_mode <> 'FIXED' OR default_amount IS NOT NULL)
);

CREATE INDEX idx_recurring_obligation_templates_workspace_status
    ON recurring_obligation_templates (workspace_id, status);

CREATE INDEX idx_recurring_obligation_templates_workspace_status_start_date
    ON recurring_obligation_templates (workspace_id, status, start_date);

CREATE TABLE obligation_occurrences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id UUID NOT NULL REFERENCES recurring_obligation_templates(id),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    period_key VARCHAR(32) NOT NULL,
    due_date DATE NOT NULL,
    reminder_date DATE,
    expected_amount NUMERIC(19,2),
    actual_amount NUMERIC(19,2),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'CONFIRMED', 'SKIPPED', 'CANCELLED')),
    snoozed_until DATE,
    linked_transaction_id UUID REFERENCES transactions(id),
    completed_at TIMESTAMPTZ,
    skipped_at TIMESTAMPTZ,
    skip_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (template_id, period_key),
    CHECK (expected_amount IS NULL OR expected_amount > 0),
    CHECK (actual_amount IS NULL OR actual_amount > 0),
    CHECK (
        (status = 'CONFIRMED' AND linked_transaction_id IS NOT NULL AND actual_amount IS NOT NULL AND completed_at IS NOT NULL)
        OR (status = 'PENDING' AND linked_transaction_id IS NULL AND completed_at IS NULL AND skipped_at IS NULL)
        OR (status = 'SKIPPED' AND linked_transaction_id IS NULL AND skipped_at IS NOT NULL)
        OR (status = 'CANCELLED' AND linked_transaction_id IS NULL)
    )
);

CREATE UNIQUE INDEX uq_obligation_occurrences_linked_transaction
    ON obligation_occurrences (linked_transaction_id)
    WHERE linked_transaction_id IS NOT NULL;

CREATE INDEX idx_obligation_occurrences_workspace_status_due_date
    ON obligation_occurrences (workspace_id, status, due_date);

CREATE INDEX idx_obligation_occurrences_workspace_snoozed_until
    ON obligation_occurrences (workspace_id, snoozed_until);

CREATE INDEX idx_obligation_occurrences_template_due_date
    ON obligation_occurrences (template_id, due_date);
