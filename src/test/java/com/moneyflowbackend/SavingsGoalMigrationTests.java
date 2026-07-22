package com.moneyflowbackend;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SavingsGoalMigrationTests {
    private static final Path GOALS = Path.of("src/main/resources/db/migration/V16__savings_goals.sql");
    private static final Path LEDGER = Path.of("src/main/resources/db/migration/V17__savings_goal_ledger.sql");

    @Test
    void v16CreatesSavingsGoalPlanningTableOnly() throws Exception {
        String sql = Files.readString(GOALS);
        String normalized = sql.toLowerCase();

        assertThat(GOALS).exists();
        assertThat(Pattern.compile("create\\s+table", Pattern.CASE_INSENSITIVE).matcher(sql).results()).hasSize(1);
        assertThat(normalized).contains("create table savings_goals");
        assertThat(normalized).contains("workspace_id uuid not null references workspaces(id)");
        assertThat(normalized).contains("created_by_user_id uuid not null references users(id)");
        assertThat(normalized).contains("target_amount numeric(19,2) not null check (target_amount > 0)");
        assertThat(normalized).contains("status in ('active', 'paused', 'completed', 'archived')");
        assertThat(normalized).contains("unique index uq_savings_goals_workspace_open_name");
        assertNoFinancialSideEffects(normalized);
    }

    @Test
    void v17CreatesExplicitSignedLedgerOnly() throws Exception {
        String sql = Files.readString(LEDGER);
        String normalized = sql.toLowerCase();

        assertThat(LEDGER).exists();
        assertThat(Pattern.compile("create\\s+table", Pattern.CASE_INSENSITIVE).matcher(sql).results()).hasSize(1);
        assertThat(normalized).contains("create table savings_goal_ledger_entries");
        assertThat(normalized).contains("savings_goal_id uuid not null references savings_goals(id)");
        assertThat(normalized).contains("actor_user_id uuid not null references users(id)");
        assertThat(normalized).contains("entry_type in ('contribution', 'release')");
        assertThat(normalized).contains("amount_delta numeric(19,2) not null check (amount_delta <> 0)");
        assertThat(normalized).contains("entry_type = 'contribution' and amount_delta > 0");
        assertThat(normalized).contains("entry_type = 'release' and amount_delta < 0");
        assertNoFinancialSideEffects(normalized);
    }

    private void assertNoFinancialSideEffects(String normalizedSql) {
        assertThat(normalizedSql).doesNotContain("transactions ");
        assertThat(normalizedSql).doesNotContain("wallets ");
        assertThat(normalizedSql).doesNotContain("insert into");
        assertThat(normalizedSql).doesNotContain("update ");
        assertThat(normalizedSql).doesNotContain("drop ");
        assertThat(normalizedSql).doesNotContain("truncate ");
    }
}
