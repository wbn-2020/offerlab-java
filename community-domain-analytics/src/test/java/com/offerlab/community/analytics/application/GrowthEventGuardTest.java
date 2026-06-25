package com.offerlab.community.analytics.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GrowthEventGuardTest {

    @Test
    void stageOneGrowthEventFoundationMustExist() throws Exception {
        String migration = read("../db/migration/20260623_growth_event.sql");
        String initSql = read("../db/init/05_analytics.sql");
        String trackCmd = read("src/main/java/com/offerlab/community/analytics/api/dto/GrowthEventTrackCmd.java");
        String summaryDto = read("src/main/java/com/offerlab/community/analytics/api/dto/GrowthEventSummaryDTO.java");
        String po = read("src/main/java/com/offerlab/community/analytics/infrastructure/persistence/po/GrowthEventPO.java");
        String mapper = read("src/main/java/com/offerlab/community/analytics/infrastructure/persistence/mapper/GrowthEventMapper.java");
        String service = read("src/main/java/com/offerlab/community/analytics/application/GrowthEventService.java");
        String controller = read("src/main/java/com/offerlab/community/analytics/controller/GrowthEventController.java");
        String analyticsController = read("src/main/java/com/offerlab/community/analytics/controller/AnalyticsController.java");
        String migrationCheck = read("../community-infrastructure/src/main/java/com/offerlab/community/infra/db/MigrationCheckService.java");
        String schemaReadiness = read("../scripts/check-schema-readiness.mjs");

        assertContains(migration, "t_growth_event");
        assertContains(migration, "event_type");
        assertContains(migration, "target_type");
        assertContains(migration, "target_value");
        assertContains(migration, "source_page");
        assertContains(migration, "idx_growth_event_type_time");
        assertContains(migration, "idx_growth_event_domain_time");

        assertContains(initSql, "t_growth_event");
        assertContains(initSql, "idx_growth_event_type_time");

        assertContains(trackCmd, "private String eventType");
        assertContains(trackCmd, "private Integer domain");
        assertContains(trackCmd, "private Long contentId");
        assertContains(summaryDto, "private Long total");
        assertContains(summaryDto, "private java.util.List");

        assertContains(po, "t_growth_event");
        assertContains(mapper, "tableExists()");
        assertContains(mapper, "insertEvent");
        assertContains(mapper, "countByEventType");
        assertContains(mapper, "countByDomain");
        assertContains(mapper, "countByDate");

        assertContains(service, "PUBLIC_POST_VIEW");
        assertContains(service, "AUTH_REDIRECT_CLICK");
        assertContains(service, "POST_LIKE");
        assertContains(service, "FIRST_POST_PUBLISHED");
        assertContains(service, "USER_REGISTER");
        assertContains(service, "CLIENT_TRACKABLE_EVENTS");
        assertContains(service, "if (!CLIENT_TRACKABLE_EVENTS.contains(eventType))");
        assertContains(service, "tableReady()");
        assertContains(service, "insertQuietly");
        assertContains(service, "summary(");

        assertContains(controller, "@RequestMapping(\"/api/v1/growth\")");
        assertContains(controller, "@PublicApi");
        assertContains(controller, "@PostMapping(\"/track\")");

        assertContains(analyticsController, "@GetMapping(\"/growth\")");
        assertContains(analyticsController, "adminPermissionService");
        assertContains(analyticsController, "growthEventService.summary");

        assertContains(migrationCheck, "\"t_growth_event\"");
        assertContains(migrationCheck, "growthEventReady()");
        assertContains(schemaReadiness, "'t_growth_event'");
        assertContains(schemaReadiness, "20260623_growth_event.sql");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static void assertContains(String source, String expected) {
        assertTrue(source.contains(expected), "Expected source to contain: " + expected);
    }
}
