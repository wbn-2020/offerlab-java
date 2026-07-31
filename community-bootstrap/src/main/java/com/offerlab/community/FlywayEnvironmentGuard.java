package com.offerlab.community;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

final class FlywayEnvironmentGuard
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Set<String> GUARDED_PROFILES =
            Set.of("prod", "production", "acceptance");
    private static final Set<String> CORE_FLYWAY_LOCATIONS =
            Set.of("classpath:db/flyway/core");

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        Environment environment = applicationContext.getEnvironment();
        Set<String> activeProfiles = Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (activeProfiles.stream().noneMatch(GUARDED_PROFILES::contains)) {
            return;
        }

        String locations = environment.getProperty(
                "spring.flyway.locations",
                "classpath:db/flyway/core");
        String historyTable = environment.getProperty(
                "spring.flyway.table",
                "flyway_schema_history");
        require(environment.getProperty(
                        "spring.flyway.enabled",
                        Boolean.class,
                        true),
                "prod/acceptance must keep Flyway enabled");
        Set<String> configuredLocations = Arrays.stream(locations.split(","))
                .map(String::trim)
                .filter(location -> !location.isEmpty())
                .map(location -> location.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        require(CORE_FLYWAY_LOCATIONS.equals(configuredLocations),
                "prod/acceptance must load only the core Flyway location");
        require("flyway_schema_history".equalsIgnoreCase(historyTable.trim()),
                "prod/acceptance must use the core Flyway history table");
        require(!environment.getProperty(
                        "spring.flyway.baseline-on-migrate",
                        Boolean.class,
                        false),
                "prod/acceptance must not enable Flyway baseline-on-migrate");
        require(environment.getProperty(
                        "spring.flyway.validate-on-migrate",
                        Boolean.class,
                        true),
                "prod/acceptance must keep Flyway validation enabled");
        require(!environment.getProperty(
                        "spring.flyway.out-of-order",
                        Boolean.class,
                        false),
                "prod/acceptance must not enable out-of-order migrations");
        require(environment.getProperty(
                        "spring.flyway.clean-disabled",
                        Boolean.class,
                        true),
                "prod/acceptance must keep Flyway clean disabled");
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
