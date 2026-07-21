package com.moneyflowbackend;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SpendingScopeMigrationTests {
    private static final Path MIGRATION = Path.of("src/main/resources/db/migration/V10__spending_scope_foundation.sql");

    @Test
    void migrationAddsNullableColumnsWithoutBackfillDefaultsOrIndexes() throws Exception {
        String sql = Files.readString(MIGRATION);
        String normalized = sql.toLowerCase();

        assertThat(sql).contains("ADD COLUMN spending_scope VARCHAR(20)");
        assertThat(sql).contains("ADD COLUMN default_spending_scope VARCHAR(20)");
        assertThat(normalized).doesNotContain("not null");
        assertThat(normalized).doesNotContain(" default ");
        assertThat(normalized).doesNotContain("insert into");
        assertThat(normalized).doesNotContain("update ");
        assertThat(normalized).doesNotContain("create index");
        assertThat(normalized).doesNotContain("cascade");
    }

    @Test
    void migrationContainsEnumAndExpenseOnlyConstraints() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("chk_transactions_spending_scope_values");
        assertThat(sql).contains("chk_transactions_spending_scope_expense_only");
        assertThat(sql).contains("chk_categories_default_spending_scope_values");
        assertThat(sql).contains("chk_categories_default_spending_scope_expense_only");
        assertThat(sql).contains("spending_scope IN ('PERSONAL', 'FAMILY', 'SHARED', 'WORK', 'OTHER')");
        assertThat(sql).contains("default_spending_scope IN ('PERSONAL', 'FAMILY', 'SHARED', 'WORK', 'OTHER')");
        assertThat(sql).contains("spending_scope IS NULL");
        assertThat(sql).contains("transaction_type = 'EXPENSE'");
        assertThat(sql).contains("default_spending_scope IS NULL");
        assertThat(sql).contains("category_type = 'EXPENSE'");
        assertThat(Pattern.compile("HOUSEHOLD|ESSENTIAL|DISCRETIONARY|COMMITTED|BUSINESS|NONE|UNKNOWN")
                .matcher(sql).find()).isFalse();
    }
}
