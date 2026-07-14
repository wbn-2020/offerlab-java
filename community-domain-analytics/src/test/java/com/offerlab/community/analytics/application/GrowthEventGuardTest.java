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
        String trustedMigration = read("../db/migration/20260713_trusted_content_stage1.sql");
        String effectiveReadService = read("src/main/java/com/offerlab/community/analytics/application/EffectiveReadService.java");
        String growthInsightMapper = read("src/main/java/com/offerlab/community/analytics/infrastructure/persistence/mapper/GrowthInsightMapper.java");
        String effectiveReadSession = read("src/main/java/com/offerlab/community/analytics/api/dto/EffectiveReadSessionDTO.java");
        String effectiveReadComplete = read("src/main/java/com/offerlab/community/analytics/api/dto/EffectiveReadCompleteCmd.java");
        String effectiveReadHeartbeatCmd = read("src/main/java/com/offerlab/community/analytics/api/dto/EffectiveReadHeartbeatCmd.java");
        String effectiveReadHeartbeatDto = read("src/main/java/com/offerlab/community/analytics/api/dto/EffectiveReadHeartbeatDTO.java");
        String effectiveReadCompleteDto = read("src/main/java/com/offerlab/community/analytics/api/dto/EffectiveReadCompleteDTO.java");

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
        assertContains(controller, "@RateLimit(key = \"'growth:track:' + #http.remoteAddr\", rate = 120, per = 60)");

        assertContains(analyticsController, "@GetMapping(\"/growth\")");
        assertContains(analyticsController, "adminPermissionService");
        assertContains(analyticsController, "growthEventService.summary");

        assertContains(migrationCheck, "\"t_growth_event\"");
        assertContains(migrationCheck, "growthEventReady()");
        assertContains(schemaReadiness, "'t_growth_event'");
        assertContains(schemaReadiness, "20260623_growth_event.sql");

        assertContains(trustedMigration, "event_key");
        assertContains(trustedMigration, "uk_growth_event_key");
        assertContains(initSql, "event_key");
        assertContains(po, "private String eventKey");
        assertContains(mapper, "event_key");
        assertContains(service, "EFFECTIVE_READ");
        assertContains(service, "eventKey");
        assertContains(effectiveReadSession, "private String sessionToken");
        assertContains(effectiveReadSession, "private Integer minimumActiveSeconds");
        assertContains(effectiveReadSession, "private Integer heartbeatIntervalSeconds");
        assertContains(effectiveReadSession, "private Integer heartbeatTimeoutSeconds");
        assertContains(effectiveReadSession, "private Long nextHeartbeatSeq");
        assertContains(effectiveReadSession, "private Integer activeSeconds");
        assertContains(effectiveReadSession, "private Integer maxScrollPercent");
        assertContains(effectiveReadSession, "private Boolean qualified");
        assertContains(effectiveReadComplete, "private String sessionToken");
        assertTrue(!effectiveReadComplete.contains("activeSeconds")
                        && !effectiveReadComplete.contains("scrollPercent"),
                "complete must not trust client-provided active time or scroll depth");
        assertContains(effectiveReadHeartbeatCmd, "private String sessionToken");
        assertContains(effectiveReadHeartbeatCmd, "private Long heartbeatSeq");
        assertContains(effectiveReadHeartbeatCmd, "private String activityState");
        assertContains(effectiveReadHeartbeatCmd, "private Integer scrollPercent");
        assertContains(effectiveReadHeartbeatDto, "private Boolean accepted");
        assertContains(effectiveReadHeartbeatDto, "private Boolean countingActive");
        assertContains(effectiveReadHeartbeatDto, "private Long nextHeartbeatSeq");
        assertContains(effectiveReadHeartbeatDto, "private Integer activeSeconds");
        assertContains(effectiveReadHeartbeatDto, "private Integer maxScrollPercent");
        assertContains(effectiveReadHeartbeatDto, "private Boolean qualified");
        assertContains(effectiveReadCompleteDto, "private Boolean recorded");
        assertContains(effectiveReadCompleteDto, "private Integer activeSeconds");
        assertContains(effectiveReadCompleteDto, "private Integer maxScrollPercent");
        assertContains(effectiveReadService, "opsForValue().set");
        assertTrue(!effectiveReadService.contains("getAndDelete"),
                "complete must not consume the session before validation and trusted event persistence");
        assertContains(effectiveReadService, "Duration.ofMinutes");
        assertContains(effectiveReadService, "MINIMUM_ACTIVE_SECONDS");
        assertContains(effectiveReadService, "MINIMUM_SCROLL_PERCENT");
        assertContains(effectiveReadService, "HEARTBEAT_INTERVAL_SECONDS");
        assertContains(effectiveReadService, "HEARTBEAT_TIMEOUT_SECONDS");
        assertContains(effectiveReadService, "MAX_ACTIVE_SESSIONS_PER_USER");
        assertContains(effectiveReadService, "activeSessionKey(uid, postId)");
        assertContains(effectiveReadService, "opsForZSet().zCard");
        assertContains(effectiveReadService, "ErrorCode.CACHE_ERROR.getCode().equals(e.getCode())");
        assertContains(effectiveReadService, "setIfAbsent(startLockKey(uid)");
        assertContains(effectiveReadService.replaceAll("\\s+", ""),
                "setIfAbsent(mutationLockKey(sessionToken)");
        assertContains(effectiveReadService, "CLAIM_OR_RENEW_READER_LEASE_SCRIPT");
        assertContains(effectiveReadService, "readerLeaseKey(uid)");
        assertContains(effectiveReadService, "lastHeartbeatSeq");
        assertContains(effectiveReadService, "lastHeartbeatAtEpochMillis");
        assertContains(effectiveReadService, "segmentActive");
        assertContains(effectiveReadService, "activeMillis");
        assertContains(effectiveReadService, "maxScrollPercent");
        assertContains(effectiveReadService, "persistSession");
        assertContains(effectiveReadService, "FENCED_SESSION_WRITE_SCRIPT");
        assertContains(effectiveReadService, "persistSession(mutation.state(), now, lockOwner)");
        assertContains(effectiveReadService, "persistSession(completedState, now, lockOwner)");
        assertContains(effectiveReadService, "redis.execute");
        assertContains(effectiveReadService, "mutationLockKey(state.sessionToken())");
        assertContains(effectiveReadService, "effective-read:v1:");
        assertContains(effectiveReadService, "ZoneOffset.UTC");
        assertTrue(!effectiveReadService.contains("cmd.getActiveSeconds()")
                        && !effectiveReadService.contains("cmd.getScrollPercent()"),
                "complete must qualify reads from server-maintained session state only");
        assertContains(effectiveReadService, "completedAtEpochMillis");
        assertContains(effectiveReadService, "growthEventService.recordTrusted");
        assertContains(effectiveReadService, "if (!recorded)");
        assertContains(effectiveReadService, "ErrorCode.DEPENDENCY_ERROR");
        assertContains(effectiveReadService, "markCompleted");
        assertContains(effectiveReadService, "abandon(");
        assertContains(effectiveReadService, "removeActiveSession");
        assertContains(effectiveReadService, "redis.delete(redisKey(sessionToken))");
        assertContains(effectiveReadService, "releaseReaderLeaseQuietly");
        assertContains(effectiveReadService, "releaseLock");
        assertContains(effectiveReadService, "authorId.equals(viewerUid)");
        assertContains(growthInsightMapper, "p.author_id AS authorId");
        assertContains(controller, "@PostMapping(\"/effective-read/session\")");
        assertContains(controller,
                "@RateLimit(key = \"'growth:effective-read:start:' + #uid\", rate = 30, per = 60, failOpen = false)");
        assertContains(controller, "@PostMapping(\"/effective-read/heartbeat\")");
        assertContains(controller,
                "@RateLimit(key = \"'growth:effective-read:heartbeat:' + #uid\", rate = 240, per = 60, failOpen = false)");
        assertContains(controller, "@PostMapping(\"/effective-read/complete\")");
        assertContains(controller,
                "@RateLimit(key = \"'growth:effective-read:complete:' + #uid\", rate = 120, per = 60, failOpen = false)");
        assertContains(controller, "@PostMapping(\"/effective-read/abandon\")");
        assertContains(controller,
                "@RateLimit(key = \"'growth:effective-read:abandon:' + #uid\", rate = 120, per = 60, failOpen = false)");
        assertContains(controller, "UserContext.require()");
        assertTrue(!service.contains("CLIENT_TRACKABLE_EVENTS = Set.of(\"AUTH_REDIRECT_CLICK\", \"EFFECTIVE_READ\")"),
                "effective reads must never become directly client-trackable");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static void assertContains(String source, String expected) {
        assertTrue(source.contains(expected), "Expected source to contain: " + expected);
    }
}
