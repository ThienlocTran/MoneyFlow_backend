ALTER TABLE recurring_obligation_templates
    ADD COLUMN spending_scope VARCHAR(20);

ALTER TABLE recurring_obligation_templates
    ADD CONSTRAINT chk_recurring_obligation_templates_spending_scope_values
        CHECK (
            spending_scope IS NULL
            OR spending_scope IN ('PERSONAL', 'FAMILY', 'SHARED', 'WORK', 'OTHER')
        ),
    ADD CONSTRAINT chk_recurring_obligation_templates_spending_scope_payable_only
        CHECK (
            spending_scope IS NULL
            OR direction = 'PAYABLE'
        );
