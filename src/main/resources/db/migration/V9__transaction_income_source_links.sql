ALTER TABLE income_sources
    ADD CONSTRAINT uq_income_sources_workspace_id UNIQUE (workspace_id, id);

ALTER TABLE transactions
    ADD COLUMN income_source_id UUID,
    ADD COLUMN related_income_source_id UUID;

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_income_source_workspace
        FOREIGN KEY (workspace_id, income_source_id)
        REFERENCES income_sources (workspace_id, id),
    ADD CONSTRAINT fk_transactions_related_income_source_workspace
        FOREIGN KEY (workspace_id, related_income_source_id)
        REFERENCES income_sources (workspace_id, id),
    ADD CONSTRAINT chk_transactions_income_source_links
        CHECK (
            (income_source_id IS NULL OR transaction_type = 'INCOME')
            AND (related_income_source_id IS NULL OR transaction_type = 'EXPENSE')
            AND NOT (income_source_id IS NOT NULL AND related_income_source_id IS NOT NULL)
        );

CREATE INDEX idx_transactions_income_source_posted_date
    ON transactions (workspace_id, income_source_id, transaction_date DESC)
    WHERE income_source_id IS NOT NULL
      AND transaction_status = 'POSTED'
      AND deleted_at IS NULL;

CREATE INDEX idx_transactions_related_income_source_posted_date
    ON transactions (workspace_id, related_income_source_id, transaction_date DESC)
    WHERE related_income_source_id IS NOT NULL
      AND transaction_status = 'POSTED'
      AND deleted_at IS NULL;
