ALTER TABLE transactions
    ADD COLUMN spending_scope VARCHAR(20);

ALTER TABLE categories
    ADD COLUMN default_spending_scope VARCHAR(20);

ALTER TABLE transactions
    ADD CONSTRAINT chk_transactions_spending_scope_values
        CHECK (
            spending_scope IS NULL
            OR spending_scope IN ('PERSONAL', 'FAMILY', 'SHARED', 'WORK', 'OTHER')
        ),
    ADD CONSTRAINT chk_transactions_spending_scope_expense_only
        CHECK (
            spending_scope IS NULL
            OR transaction_type = 'EXPENSE'
        );

ALTER TABLE categories
    ADD CONSTRAINT chk_categories_default_spending_scope_values
        CHECK (
            default_spending_scope IS NULL
            OR default_spending_scope IN ('PERSONAL', 'FAMILY', 'SHARED', 'WORK', 'OTHER')
        ),
    ADD CONSTRAINT chk_categories_default_spending_scope_expense_only
        CHECK (
            default_spending_scope IS NULL
            OR category_type = 'EXPENSE'
        );
