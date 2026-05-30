package com.offerlab.community.search.controller;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchAnalyticsGuardTest {

    @Test
    void searchAnalyticsMustRecordExposeAndRenderOperationalStats() throws Exception {
        String initSql = Files.readString(Path.of("../db/init/05_analytics.sql"), StandardCharsets.UTF_8);
        String migrationSql = Files.readString(Path.of("../db/migration/20260530_search_analytics.sql"), StandardCharsets.UTF_8);
        String mapperSource = Files.readString(Path.of("src/main/java/com/offerlab/community/search/infrastructure/persistence/mapper/SearchAnalyticsMapper.java"), StandardCharsets.UTF_8);
        String serviceSource = Files.readString(Path.of("src/main/java/com/offerlab/community/search/application/SearchAnalyticsService.java"), StandardCharsets.UTF_8);
        String facadeSource = Files.readString(Path.of("src/main/java/com/offerlab/community/search/application/SearchFacadeImpl.java"), StandardCharsets.UTF_8);
        String searchControllerSource = Files.readString(Path.of("src/main/java/com/offerlab/community/search/controller/SearchController.java"), StandardCharsets.UTF_8);
        String opsControllerSource = Files.readString(Path.of("src/main/java/com/offerlab/community/search/controller/OpsController.java"), StandardCharsets.UTF_8);

        assertTrue(initSql.contains("CREATE TABLE IF NOT EXISTS t_search_analytics_event"), "fresh init SQL must create search analytics table");
        assertTrue(migrationSql.contains("CREATE TABLE IF NOT EXISTS t_search_analytics_event"), "migration must create search analytics table without destructive reset");
        assertTrue(migrationSql.contains("idx_event_time"), "search analytics table must index event type and time");
        assertTrue(migrationSql.contains("idx_keyword_time"), "search analytics table must index keyword stats");
        assertTrue(migrationSql.contains("idx_company_time"), "search analytics table must index company prep clicks");

        assertTrue(mapperSource.contains("tableExists()"), "analytics mapper must tolerate missing migration table");
        assertTrue(mapperSource.contains("insertEvent"), "analytics mapper must insert events");
        assertTrue(mapperSource.contains("topSearchKeywords"), "analytics mapper must aggregate hot keywords");
        assertTrue(mapperSource.contains("topNoResultKeywords"), "analytics mapper must aggregate zero-result keywords");
        assertTrue(mapperSource.contains("topPrepClicks"), "analytics mapper must aggregate prep-pack clicks");
        assertTrue(mapperSource.contains("event_type = 'SEARCH'"), "search events must be distinct from click events");
        assertTrue(mapperSource.contains("event_type = 'PREP_CLICK'"), "prep click events must be queryable separately");

        assertTrue(serviceSource.contains("EVENT_SEARCH"), "analytics service must record search events");
        assertTrue(serviceSource.contains("EVENT_PREP_CLICK"), "analytics service must record prep click events");
        assertTrue(serviceSource.contains("tableReady()"), "analytics service must fail open when table is not ready");
        assertTrue(serviceSource.contains("Math.max(1, Math.min(days, 90))"), "analytics summary must clamp days");
        assertTrue(serviceSource.contains("Math.max(1, Math.min(limit, 50))"), "analytics summary must clamp limit");

        assertTrue(facadeSource.contains("boolean firstPage = parseCursor(cursor) <= 0"), "search analytics must only record first-page searches");
        assertTrue(facadeSource.contains("searchAnalyticsService.recordSearch"), "post search must record analytics after ES/MySQL search");

        assertTrue(searchControllerSource.contains("/analytics/track"), "search controller must expose client analytics tracking endpoint");
        assertTrue(searchControllerSource.contains("SearchAnalyticsTrackCmd"), "search tracking endpoint must use a typed DTO");
        assertTrue(searchControllerSource.contains("PREP_CLICK"), "search tracking endpoint must accept prep click events");
        assertTrue(searchControllerSource.contains("recordPrepClick"), "search tracking endpoint must record prep clicks");

        assertTrue(opsControllerSource.contains("/search/analytics"), "ops controller must expose search analytics summary");
        assertTrue(opsControllerSource.contains("SearchAnalyticsDTO"), "ops search analytics endpoint must return structured DTO");
        assertTrue(opsControllerSource.contains("searchAnalyticsService.summary"), "ops endpoint must call analytics summary service");
    }
}
