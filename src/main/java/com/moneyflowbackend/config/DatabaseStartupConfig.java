package com.moneyflowbackend.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.net.URI;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Configuration
public class DatabaseStartupConfig {
    private static final Logger log = LoggerFactory.getLogger(DatabaseStartupConfig.class);
    private static final Set<String> WRONG_PROJECT_SCRIPTS = Set.of(
            "v1__init_schema.sql",
            "v2__seed_base_roles.sql",
            "v3__create_warehouses_table.sql",
            "v7__align_import_receipt_workflow.sql",
            "v11__create_discrepancy_report_tables.sql"
    );

    @Bean
    FlywayMigrationStrategy moneyFlowFlywayMigrationStrategy(DataSource dataSource) {
        return flyway -> {
            failIfWrongProjectHistory(dataSource);
            flyway.migrate();
        };
    }

    @Bean
    ApplicationRunner moneyFlowDatabaseIdentityLogger(Environment environment) {
        return args -> {
            if (isTestProfile(environment)) {
                return;
            }
            String url = environment.getProperty("spring.datasource.url", "");
            DbTarget target = DbTarget.from(url);
            log.info("MoneyFlow DB target: host={}, database={}, profile={}, flyway={}",
                    target.maskedHost(),
                    target.database(),
                    String.join(",", environment.getActiveProfiles()),
                    environment.getProperty("spring.flyway.enabled", "true"));

            warnIfHostDiffers("MONEYFLOW_DB_URL", "DB_URL", environment);
            warnIfHostDiffers("MONEYFLOW_DB_URL", "SPRING_DATASOURCE_URL", environment);
        };
    }

    private static boolean isTestProfile(Environment environment) {
        for (String profile : environment.getActiveProfiles()) {
            if ("test".equals(profile)) {
                return true;
            }
        }
        return false;
    }

    private static void failIfWrongProjectHistory(DataSource dataSource) {
        Set<String> scripts = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            if (!flywayHistoryExists(statement)) {
                return;
            }
            try (ResultSet rs = statement.executeQuery("select script from flyway_schema_history where script is not null")) {
                while (rs.next()) {
                    scripts.add(rs.getString(1).toLowerCase(Locale.ROOT));
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to verify MoneyFlow database target before Flyway migration", ex);
        }

        scripts.stream()
                .filter(WRONG_PROJECT_SCRIPTS::contains)
                .findFirst()
                .ifPresent(script -> {
                    throw new IllegalStateException("WRONG_DATABASE_TARGET: Flyway history contains non-MoneyFlow script " + script);
                });
    }

    private static boolean flywayHistoryExists(Statement statement) throws Exception {
        try (ResultSet rs = statement.executeQuery("""
                select exists (
                    select 1
                    from information_schema.tables
                    where table_schema = 'public'
                      and table_name = 'flyway_schema_history'
                )
                """)) {
            return rs.next() && rs.getBoolean(1);
        }
    }

    private static void warnIfHostDiffers(String preferredKey, String fallbackKey, Environment environment) {
        String preferred = environment.getProperty(preferredKey);
        String fallback = environment.getProperty(fallbackKey);
        if (preferred == null || preferred.isBlank() || fallback == null || fallback.isBlank()) {
            return;
        }
        DbTarget preferredTarget = DbTarget.from(preferred);
        DbTarget fallbackTarget = DbTarget.from(fallback);
        if (!preferredTarget.host().isBlank()
                && !fallbackTarget.host().isBlank()
                && !preferredTarget.host().equalsIgnoreCase(fallbackTarget.host())) {
            log.warn("{} host {} differs from {} host {}; MoneyFlow uses {}",
                    fallbackKey,
                    fallbackTarget.maskedHost(),
                    preferredKey,
                    preferredTarget.maskedHost(),
                    preferredKey);
        }
    }

    private record DbTarget(String host, String database) {
        static DbTarget from(String rawUrl) {
            if (rawUrl == null || rawUrl.isBlank()) {
                return new DbTarget("", "");
            }
            String normalized = rawUrl.startsWith("jdbc:") ? rawUrl.substring(5) : rawUrl;
            try {
                URI uri = URI.create(normalized.replace("postgresql://", "postgres://"));
                String path = uri.getPath();
                return new DbTarget(uri.getHost() == null ? "" : uri.getHost(),
                        path == null || path.length() <= 1 ? "" : path.substring(1));
            } catch (Exception ex) {
                return new DbTarget("", "");
            }
        }

        String maskedHost() {
            if (host == null || host.isBlank()) {
                return "unknown";
            }
            int dot = host.indexOf('.');
            String first = dot > 0 ? host.substring(0, dot) : host;
            String suffix = dot > 0 ? host.substring(dot) : "";
            String maskedFirst = first.length() <= 6 ? first : first.substring(0, 6) + "***";
            return maskedFirst + suffix;
        }
    }
}
