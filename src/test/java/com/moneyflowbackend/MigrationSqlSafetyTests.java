package com.moneyflowbackend;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationSqlSafetyTests {
    @Test
    void v6KeepsAdjustmentDirectionConstraintNotValidAndRejectsNullDirection() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V6__enforce_adjustment_direction_nullability.sql"));

        assertThat(sql).contains("transactions_adjustment_direction_check");
        assertThat(sql).contains("adjustment_direction IS NOT NULL");
        assertThat(sql).contains("NOT VALID");
    }

    @Test
    void v7CreatesOnlyRecurringObligationPersistenceAndGuardsCoreIntegrity() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V7__recurring_obligations.sql");
        String sql = Files.readString(migration);
        String normalized = sql.toLowerCase();

        assertThat(migration).exists();
        assertThat(normalized).contains("create table recurring_obligation_templates");
        assertThat(normalized).contains("create table obligation_occurrences");
        assertThat(Pattern.compile("create\\s+table", Pattern.CASE_INSENSITIVE).matcher(sql).results()).hasSize(2);
        assertThat(normalized).doesNotContain("create table inbox");
        assertThat(normalized).doesNotContain("create table notifications");
        assertThat(normalized).doesNotContain("create table sched");
        assertThat(normalized).doesNotContain("drop ");
        assertThat(normalized).doesNotContain("truncate ");
        assertThat(normalized).doesNotContain("insert into");
        assertThat(normalized).contains("unique (template_id, period_key)");
        assertThat(normalized).contains("unique index uq_obligation_occurrences_linked_transaction");
        assertThat(normalized).contains("where linked_transaction_id is not null");
        assertThat(normalized).contains("direction in ('payable', 'receivable')");
        assertThat(normalized).contains("amount_mode in ('fixed', 'variable')");
        assertThat(normalized).contains("frequency in ('weekly', 'monthly', 'yearly')");
        assertThat(normalized).contains("status in ('active', 'paused', 'archived')");
        assertThat(normalized).contains("status in ('pending', 'confirmed', 'skipped', 'cancelled')");
        assertThat(normalized).contains("amount_mode <> 'fixed' or default_amount is not null");
        assertThat(normalized).contains("default_amount is null or default_amount > 0");
        assertThat(normalized).contains("expected_amount is null or expected_amount > 0");
        assertThat(normalized).contains("actual_amount is null or actual_amount > 0");
        assertThat(normalized).contains("status = 'confirmed' and linked_transaction_id is not null and actual_amount is not null and completed_at is not null");
        assertThat(normalized).contains("status = 'skipped' and linked_transaction_id is null and skipped_at is not null");
        assertThat(normalized).contains("workspace_id uuid not null references workspaces(id)");
        assertThat(normalized).contains("default_wallet_id uuid references wallets(id)");
        assertThat(normalized).contains("default_category_id uuid references categories(id)");
        assertThat(normalized).contains("created_by_user_id uuid not null references users(id)");
        assertThat(normalized).contains("linked_transaction_id uuid references transactions(id)");
    }

    @Test
    void v7DoesNotModifyEarlierMigrationsAndContainsNoMojibake() throws Exception {
        List<String> migrationNames = Files.list(Path.of("src/main/resources/db/migration"))
                .map(path -> path.getFileName().toString())
                .sorted()
                .toList();

        assertThat(migrationNames).containsExactly(
                "V1__create_core_schema.sql",
                "V2__historical_balance_effect_flags.sql",
                "V3__alter_voice_record_status_for_audio_storage.sql",
                "V4__transaction_list_pagination_index.sql",
                "V5__daily_closing_wallet_snapshot_reconciliation.sql",
                "V6__enforce_adjustment_direction_nullability.sql",
                "V7__recurring_obligations.sql");

        for (String migrationName : migrationNames) {
            String sql = Files.readString(Path.of("src/main/resources/db/migration", migrationName));
            assertThat(sql)
                    .doesNotContain("\uFFFD")
                    .doesNotContain(String.valueOf((char) 0x00C3))
                    .doesNotContain(String.valueOf((char) 0x00C4))
                    .doesNotContain(String.valueOf((char) 0x00C6))
                    .doesNotContain(String.valueOf((char) 0x00C2));
        }
    }
}
