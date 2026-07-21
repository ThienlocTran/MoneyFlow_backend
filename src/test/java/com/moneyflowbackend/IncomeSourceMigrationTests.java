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
        String linkageSql = Files.readString(MIGRATION_DIR.resolve("V9__transaction_income_source_links.sql"));
        List<String> migrations;
        try (Stream<Path> paths = Files.list(MIGRATION_DIR)) {
            migrations = paths
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("V\\d+__.*\\.sql"))
                    .toList();
        }

        assertThat(migrations).contains("V8__income_sources.sql");
        assertThat(migrations).contains("V9__transaction_income_source_links.sql");
        assertThat(sql).contains("CREATE TABLE income_sources");
        assertThat(sql).contains("workspace_id UUID NOT NULL REFERENCES workspaces(id)");
        assertThat(sql).contains("created_by_user_id UUID NOT NULL REFERENCES users(id)");
        assertThat(sql).contains("version BIGINT NOT NULL DEFAULT 0");
        assertThat(sql).contains("CHECK (BTRIM(name) <> '')");
        assertThat(sql).contains("CREATE UNIQUE INDEX uq_income_sources_workspace_active_name");
        assertThat(sql).contains("ON income_sources (workspace_id, LOWER(BTRIM(name)))");
        assertThat(sql).contains("WHERE status = 'ACTIVE'");
        assertThat(Pattern.compile("ON DELETE CASCADE", Pattern.CASE_INSENSITIVE).matcher(sql).find()).isFalse();
        assertThat(linkageSql).contains("ADD COLUMN income_source_id UUID");
        assertThat(linkageSql).contains("ADD COLUMN related_income_source_id UUID");
        assertThat(linkageSql).contains("FOREIGN KEY (workspace_id, income_source_id)");
        assertThat(linkageSql).contains("REFERENCES income_sources (workspace_id, id)");
        assertThat(linkageSql).contains("FOREIGN KEY (workspace_id, related_income_source_id)");
        assertThat(linkageSql).contains("chk_transactions_income_source_links");
        assertThat(linkageSql).contains("transaction_type = 'INCOME'");
        assertThat(linkageSql).contains("transaction_type = 'EXPENSE'");
        assertThat(linkageSql).contains("idx_transactions_income_source_posted_date");
        assertThat(linkageSql).contains("idx_transactions_related_income_source_posted_date");
        assertThat(Pattern.compile("CASCADE", Pattern.CASE_INSENSITIVE).matcher(linkageSql).find()).isFalse();
    }
}
