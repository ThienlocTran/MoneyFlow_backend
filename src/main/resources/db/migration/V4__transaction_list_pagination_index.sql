CREATE INDEX idx_transactions_workspace_date_created
    ON transactions (workspace_id, transaction_date DESC, created_at DESC)
    WHERE deleted_at IS NULL;
