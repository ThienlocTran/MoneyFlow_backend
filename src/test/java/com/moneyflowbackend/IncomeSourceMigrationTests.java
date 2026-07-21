package com.moneyflowbackend;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class IncomeSourceMigrationTests {
    private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");
    private static final Path MIGRATION = MIGRATION_DIR.resolve("V8__income_sources.sql");

    @Test
    void incomeSourceMigrationIsNextAndContainsPostgresSafeguards() throws IOException {
        String sql = Files.readString(MIGRATION);
        List<String> migrations;
        try (Stream<Path> paths = Files.list(MIGRATION_DIR)) {
            migrations = paths
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("V\\d+__.*\\.sql"))
                    .toList();
        }

        assertThat(migrations).contains("V8__income_sources.sql");
        assertThat(sql).contains("CREATE TABLE income_sources");
        assertThat(sql).contains("workspace_id UUID NOT NULL REFERENCES workspaces(id)");
        assertThat(sql).contains("created_by_user_id UUID NOT NULL REFERENCES users(id)");
        assertThat(sql).contains("version BIGINT NOT NULL DEFAULT 0");
        assertThat(sql).contains("CHECK (BTRIM(name) <> '')");
        assertThat(sql).contains("CREATE UNIQUE INDEX uq_income_sources_workspace_active_name");
        assertThat(sql).contains("ON income_sources (workspace_id, LOWER(BTRIM(name)))");
        assertThat(sql).contains("WHERE status = 'ACTIVE'");
        assertThat(Pattern.compile("ON DELETE CASCADE", Pattern.CASE_INSENSITIVE).matcher(sql).find()).isFalse();
    }
}
