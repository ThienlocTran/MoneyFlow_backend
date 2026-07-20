package com.moneyflowbackend;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationSqlSafetyTests {
    @Test
    void v6KeepsAdjustmentDirectionConstraintNotValidAndRejectsNullDirection() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V6__enforce_adjustment_direction_nullability.sql"));

        assertThat(sql).contains("transactions_adjustment_direction_check");
        assertThat(sql).contains("adjustment_direction IS NOT NULL");
        assertThat(sql).contains("NOT VALID");
    }
}
