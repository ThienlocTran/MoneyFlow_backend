CREATE TABLE daily_closings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    closing_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('OPEN', 'COMPLETED')),
    note TEXT,
    completed_at TIMESTAMPTZ,
    completed_by_user_id UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (workspace_id, closing_date)
);

ALTER TABLE wallet_balance_snapshots
    ADD COLUMN daily_closing_id UUID REFERENCES daily_closings(id),
    ADD COLUMN recorded_at TIMESTAMPTZ,
    ADD COLUMN created_by_user_id UUID REFERENCES users(id),
    ADD COLUMN ledger_balance NUMERIC(19,2),
    ADD COLUMN difference NUMERIC(19,2),
    ADD COLUMN reconciliation_status VARCHAR(20),
    ADD COLUMN adjustment_transaction_id UUID REFERENCES transactions(id),
    ADD COLUMN updated_at TIMESTAMPTZ,
    ADD COLUMN version BIGINT;

ALTER TABLE wallet_balance_snapshots
    DROP CONSTRAINT IF EXISTS wallet_balance_snapshots_source_type_check;

ALTER TABLE wallet_balance_snapshots
    ADD CONSTRAINT wallet_balance_snapshots_source_type_check
        CHECK (source_type IN ('MANUAL', 'VOICE', 'EXCEL_MIGRATION', 'SYSTEM'));

ALTER TABLE wallet_balance_snapshots
    ADD CONSTRAINT wallet_balance_snapshots_reconciliation_status_check
        CHECK (reconciliation_status IS NULL OR reconciliation_status IN ('UNRESOLVED', 'MATCHED', 'ADJUSTED'));

CREATE UNIQUE INDEX uq_wallet_balance_snapshots_daily_closing_wallet
    ON wallet_balance_snapshots (daily_closing_id, wallet_id)
    WHERE daily_closing_id IS NOT NULL;

ALTER TABLE transactions
    ADD COLUMN adjustment_direction VARCHAR(20);

ALTER TABLE transactions
    ADD CONSTRAINT transactions_adjustment_direction_check
        CHECK (
            (transaction_type = 'ADJUSTMENT' AND adjustment_direction IN ('INCREASE', 'DECREASE'))
            OR (transaction_type <> 'ADJUSTMENT' AND adjustment_direction IS NULL)
        ) NOT VALID;
