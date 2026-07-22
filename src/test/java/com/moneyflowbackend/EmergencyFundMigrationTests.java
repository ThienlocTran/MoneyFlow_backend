package com.moneyflowbackend;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class EmergencyFundMigrationTests {
    private static final Path PLAN = Path.of("src/main/resources/db/migration/V18__emergency_fund_plan.sql");
    private static final Path LEDGER = Path.of("src/main/resources/db/migration/V19__emergency_fund_ledger.sql");

    @Test
    void v18CreatesSingularManualPlanOnly() throws Exception {
        String sql = Files.readString(PLAN);
        String normalized = sql.toLowerCase();

        assertThat(PLAN).exists();
        assertThat(Pattern.compile("create\\s+table", Pattern.CASE_INSENSITIVE).matcher(sql).results()).hasSize(1);
        assertThat(normalized).contains("create table emergency_fund_plans");
        assertThat(normalized).contains("workspace_id uuid not null references workspaces(id)");
        assertThat(normalized).contains("created_by_user_id uuid not null references users(id)");
        assertThat(normalized).contains("target_months integer not null check (target_months > 0)");
        assertThat(normalized).contains("basis_mode in ('manual')");
        assertThat(normalized).contains("manual_monthly_expense numeric(19,2) not null check (manual_monthly_expense > 0)");
        assertThat(normalized).contains("plan_status in ('active', 'paused')");
        assertThat(normalized).contains("unique index uq_emergency_fund_plans_workspace");
        assertNoFinancialSideEffects(normalized);
    }

    @Test
    void v19CreatesExplicitAppendOnlyLedgerOnly() throws Exception {
        String sql = Files.readString(LEDGER);
        String normalized = sql.toLowerCase();

        assertThat(LEDGER).exists();
        assertThat(Pattern.compile("create\\s+table", Pattern.CASE_INSENSITIVE).matcher(sql).results()).hasSize(1);
        assertThat(normalized).contains("create table emergency_fund_ledger_entries");
        assertThat(normalized).contains("emergency_fund_plan_id uuid not null references emergency_fund_plans(id)");
        assertThat(normalized).contains("actor_user_id uuid not null references users(id)");
        assertThat(normalized).contains("entry_type in ('allocate', 'release')");
        assertThat(normalized).contains("amount_delta numeric(19,2) not null check (amount_delta <> 0)");
        assertThat(normalized).contains("entry_type = 'allocate' and amount_delta > 0");
        assertThat(normalized).contains("entry_type = 'release' and amount_delta < 0");
        assertNoFinancialSideEffects(normalized);
    }

    private void assertNoFinancialSideEffects(String normalizedSql) {
        assertThat(normalizedSql).doesNotContain("transactions ");
        assertThat(normalizedSql).doesNotContain("wallets ");
        assertThat(normalizedSql).doesNotContain("savings_goals");
        assertThat(normalizedSql).doesNotContain("sinking_funds");
        assertThat(normalizedSql).doesNotContain("insert into");
        assertThat(normalizedSql).doesNotContain("update ");
        assertThat(normalizedSql).doesNotContain("drop ");
        assertThat(normalizedSql).doesNotContain("truncate ");
    }
}
