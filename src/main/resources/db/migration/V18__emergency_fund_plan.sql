CREATE TABLE emergency_fund_plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    target_months INTEGER NOT NULL CHECK (target_months > 0),
    basis_mode VARCHAR(20) NOT NULL DEFAULT 'MANUAL'
        CHECK (basis_mode IN ('MANUAL')),
    manual_monthly_expense NUMERIC(19,2) NOT NULL CHECK (manual_monthly_expense > 0),
    plan_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (plan_status IN ('ACTIVE', 'PAUSED')),
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_emergency_fund_plans_workspace
    ON emergency_fund_plans (workspace_id);
