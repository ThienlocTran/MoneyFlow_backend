package com.moneyflowbackend.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

public class MoneyFlowEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    private static final String PROPERTY_SOURCE = "moneyflowDatasourceOverrides";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (isTestProfile(environment)) {
            return;
        }
        Map<String, Object> overrides = new LinkedHashMap<>();
        putFirstPresent(environment, overrides, "spring.datasource.url",
                "MONEYFLOW_DB_URL", "SPRING_DATASOURCE_URL", "DB_URL");
        putFirstPresent(environment, overrides, "spring.datasource.username",
                "MONEYFLOW_DB_USERNAME", "SPRING_DATASOURCE_USERNAME", "DB_USERNAME");
        putFirstPresent(environment, overrides, "spring.datasource.password",
                "MONEYFLOW_DB_PASSWORD", "SPRING_DATASOURCE_PASSWORD", "DB_PASSWORD");

        if (!overrides.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE, overrides));
        }
    }

    private boolean isTestProfile(ConfigurableEnvironment environment) {
        for (String profile : environment.getActiveProfiles()) {
            if ("test".equals(profile)) {
                return true;
            }
        }
        return false;
    }

    private void putFirstPresent(ConfigurableEnvironment environment, Map<String, Object> overrides,
                                 String property, String... keys) {
        for (String key : keys) {
            String value = environment.getProperty(key);
            if (value != null && !value.isBlank()) {
                overrides.put(property, value);
                return;
            }
        }
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }
}
