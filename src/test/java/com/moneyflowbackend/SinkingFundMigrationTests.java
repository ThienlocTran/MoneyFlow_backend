package com.moneyflowbackend;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SinkingFundMigrationTests {
    private static final Path MIGRATION = Path.of("src/main/resources/db/migration/V12__sinking_funds.sql");

    @Test
    void v12CreatesOnlySinkingFundFoundationAndNoRuntimeFinancialSideEffects() throws Exception {
        String sql = Files.readString(MIGRATION);
        String normalized = sql.toLowerCase();

        assertThat(MIGRATION).exists();
        assertThat(Pattern.compile("create\\s+table", Pattern.CASE_INSENSITIVE).matcher(sql).results()).hasSize(2);
        assertThat(normalized).contains("create table sinking_funds");
        assertThat(normalized).contains("create table sinking_fund_allocations");
        assertThat(normalized).contains("workspace_id uuid not null references workspaces(id)");
        assertThat(normalized).contains("created_by_user_id uuid not null references users(id)");
        assertThat(normalized).contains("actor_user_id uuid not null references users(id)");
        assertThat(normalized).contains("target_amount numeric(19,2) check (target_amount is null or target_amount > 0)");
        assertThat(normalized).contains("amount_delta numeric(19,2) not null check (amount_delta <> 0)");
        assertThat(normalized).contains("status in ('active', 'paused', 'completed', 'archived')");
        assertThat(normalized).contains("allocation_type in ('allocate', 'release', 'adjust')");
        assertThat(normalized).contains("unique index uq_sinking_funds_workspace_open_name");
        assertThat(normalized).doesNotContain("transactions ");
        assertThat(normalized).doesNotContain("wallets ");
        assertThat(normalized).doesNotContain("insert into");
        assertThat(normalized).doesNotContain("update ");
        assertThat(normalized).doesNotContain("drop ");
        assertThat(normalized).doesNotContain("truncate ");
    }

    @Test
    void sinkingFundMigrationsStayInV12OrV13AndUsePostgresqlObjectsOnlyInLocalFiles() throws Exception {
        List<String> migrations;
        try (Stream<Path> paths = Files.list(Path.of("src/main/resources/db/migration"))) {
            migrations = paths
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.contains("sinking_fund"))
                    .sorted()
                    .toList();
        }

        assertThat(migrations).containsExactly("V12__sinking_funds.sql");
        String sql = Files.readString(MIGRATION).toLowerCase();
        assertThat(sql).contains("gen_random_uuid()");
        assertThat(sql).contains("timestamptz");
        assertThat(sql).contains("where status <> 'archived'");
        assertThat(sql).doesNotContain("jdbc:");
        assertThat(sql).doesNotContain("spring.profiles.active");
    }
}
