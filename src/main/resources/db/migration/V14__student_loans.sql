CREATE TABLE student_loans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    lender VARCHAR(160),
    original_principal NUMERIC(19,2),
    current_principal NUMERIC(19,2) NOT NULL,
    annual_interest_rate NUMERIC(9,6) NOT NULL,
    minimum_monthly_payment NUMERIC(19,2) NOT NULL,
    planned_extra_monthly_payment NUMERIC(19,2),
    start_date DATE,
    target_payoff_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (BTRIM(name) <> ''),
    CHECK (lender IS NULL OR BTRIM(lender) <> ''),
    CHECK (original_principal IS NULL OR original_principal >= 0),
    CHECK (current_principal >= 0),
    CHECK (annual_interest_rate >= 0),
    CHECK (minimum_monthly_payment >= 0),
    CHECK (planned_extra_monthly_payment IS NULL OR planned_extra_monthly_payment >= 0),
    CHECK (target_payoff_date IS NULL OR start_date IS NULL OR target_payoff_date >= start_date),
    CHECK (status IN ('ACTIVE', 'PAID_OFF', 'PAUSED', 'ARCHIVED'))
);

CREATE INDEX idx_student_loans_workspace_status
    ON student_loans (workspace_id, status);

CREATE INDEX idx_student_loans_workspace_status_name
    ON student_loans (workspace_id, status, LOWER(BTRIM(name)), id);
