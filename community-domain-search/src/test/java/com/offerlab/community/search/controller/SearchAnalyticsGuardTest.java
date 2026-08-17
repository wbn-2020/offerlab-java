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
        assertTrue(migrationSql.contains("idx_company_time"), "search analytics table must index click targets");

        assertTrue(mapperSource.contains("tableExists()"), "analytics mapper must tolerate missing migration table");
        assertTrue(mapperSource.contains("insertEvent"), "analytics mapper must insert events");
        assertTrue(mapperSource.contains("topSearchKeywords"), "analytics mapper must aggregate hot keywords");
        assertTrue(mapperSource.contains("topNoResultKeywords"), "analytics mapper must aggregate zero-result keywords");
        assertTrue(mapperSource.contains("topRecommendationClicks"), "analytics mapper must aggregate community recommendation clicks");
        assertTrue(mapperSource.contains("event_type = 'SEARCH'"), "search events must be distinct from click events");
        assertTrue(mapperSource.contains("WHERE event_type = 'COMMUNITY_RECOMMEND_CLICK'"),
                "community recommendation analytics must not mix prep-click targets into recommendation stats");
        assertTrue(mapperSource.contains("COMMUNITY_RECOMMEND_CLICK"), "community recommendation click events must be queryable separately");
        assertTrue(mapperSource.contains("includeTestData"), "ops analytics must be able to explicitly include test data");
        assertTrue(mapperSource.contains("NOT LIKE '%E2E%'"), "ops analytics must hide E2E data by default");
        assertTrue(mapperSource.contains("NOT LIKE '%CODEX%'"), "ops analytics must hide Codex test data by default");

        assertTrue(serviceSource.contains("EVENT_SEARCH"), "analytics service must record search events");
        assertTrue(serviceSource.contains("EVENT_COMMUNITY_RECOMMEND_CLICK"), "analytics service must record community recommendation click events");
        assertTrue(serviceSource.contains("recordCommunityRecommendClick"), "analytics service must expose community recommendation tracking");
        assertTrue(!serviceSource.contains("UserContext"), "search analytics must not collect user identifiers for profiling");
        assertTrue(serviceSource.contains("event.setUid(null)"), "search analytics must write anonymous aggregate events only");
        assertTrue(serviceSource.contains("mapper.topPrepClicks"), "prep click analytics must stay separated from recommendation click analytics");
        assertTrue(serviceSource.contains("tableReady()"), "analytics service must fail open when table is not ready");
        assertTrue(serviceSource.contains("Math.max(1, Math.min(days, 90))"), "analytics summary must clamp days");
        assertTrue(serviceSource.contains("Math.max(1, Math.min(limit, 50))"), "analytics summary must clamp limit");

        assertTrue(facadeSource.contains(
                        "SearchCursor searchCursor = trustedSort ? null : parseSearchCursor(cursor, normalizedSort)"),
                "search analytics must parse the normalized search cursor once");
        assertTrue(facadeSource.contains(": !searchCursor.present();"),
                "search analytics must only record the first page for both trusted-offset and search cursors");
        assertTrue(facadeSource.contains("searchAnalyticsService.recordSearch"), "post search must record analytics after ES/MySQL search");

        assertTrue(searchControllerSource.contains("/analytics/track"), "search controller must expose client analytics tracking endpoint");
        assertTrue(searchControllerSource.contains("SearchAnalyticsTrackCmd"), "search tracking endpoint must use a typed DTO");
        assertTrue(searchControllerSource.contains("COMMUNITY_RECOMMEND_CLICK"), "search tracking endpoint must accept community recommendation click events");
        assertTrue(searchControllerSource.contains("recordCommunityRecommendClick"), "search tracking endpoint must record community recommendation clicks");
        assertTrue(searchControllerSource.contains("Cache<String, RateBucket> TRACK_RATE_BUCKETS = Caffeine.newBuilder()"),
                "tracking rate buckets must use the repository Caffeine pattern");
        assertTrue(searchControllerSource.contains("Cache<String, DedupReservation> TRACK_DEDUP_KEYS = Caffeine.newBuilder()"),
                "tracking dedup keys must use the repository Caffeine pattern");
        assertTrue(searchControllerSource.contains(".maximumSize(TRACK_GUARD_MAX_ENTRIES)"),
                "tracking guards must have a hard configured entry ceiling");
        assertTrue(searchControllerSource.contains(".expireAfterWrite(Duration.ofMillis(TRACK_RATE_WINDOW_MS))")
                        && searchControllerSource.contains(".expireAfterWrite(Duration.ofMillis(TRACK_DEDUP_WINDOW_MS))"),
                "tracking guards must expire without request-thread table scans");
        assertTrue(searchControllerSource.contains(".asMap().putIfAbsent(dedupKey, reservation)"),
                "dedup reservation must be atomic");
        assertTrue(searchControllerSource.contains(".asMap().remove(dedupKey, reservation)"),
                "failed tracking must not remove a newer dedup reservation");
        assertTrue(!searchControllerSource.contains("cleanupTrackGuards"),
                "request threads must not scan tracking guard tables");
        assertTrue(!searchControllerSource.contains("ConcurrentHashMap"),
                "tracking guards must not regress to manually managed unbounded maps");

        assertTrue(opsControllerSource.contains("/search/analytics"), "ops controller must expose search analytics summary");
        assertTrue(opsControllerSource.contains("SearchAnalyticsDTO"), "ops search analytics endpoint must return structured DTO");
        assertTrue(opsControllerSource.contains("searchAnalyticsService.summary"), "ops endpoint must call analytics summary service");
        assertTrue(opsControllerSource.contains("includeTestData"), "ops endpoint must expose an explicit test-data switch");
    }
    @Test
    void mysqlFallbackSearchMustStayBoundedAndDatabaseFiltered() throws Exception {
        String facadeSource = Files.readString(Path.of("src/main/java/com/offerlab/community/search/application/SearchFacadeImpl.java"), StandardCharsets.UTF_8);
        String postMapperSource = Files.readString(Path.of("../community-domain-post/src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/PostMapper.java"), StandardCharsets.UTF_8);
        String tagMapperSource = Files.readString(Path.of("../community-domain-post/src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/TagMapper.java"), StandardCharsets.UTF_8);

        assertTrue(facadeSource.contains("MYSQL_FALLBACK_MAX_SCAN"), "MySQL fallback must have an explicit scan ceiling");
        assertTrue(facadeSource.contains("fallbackScanLimit(limit)"), "MySQL fallback must clamp per-request scan size");
        assertTrue(facadeSource.contains("postMapper.searchPublicPostsFallback"), "search fallback must use a mapper query");
        assertTrue(facadeSource.contains("SearchQuerySpec.from(keyword)"), "ES and MySQL fallback must share one query normalization contract");
        assertTrue(facadeSource.contains("querySpec.minimumTermMatches()"), "fallback must use the same dynamic minimum-match threshold");
        assertTrue(facadeSource.contains("postMapper.suggestPublicPostsFallback"), "suggest fallback must use a mapper query");
        assertTrue(facadeSource.contains("emptyHints"), "empty search results must expose metadata hints instead of fake results");
        assertTrue(facadeSource.contains("mysql_fallback_no_hit_explanation"), "MySQL fallback must clearly downgrade hit explanation support");
        assertTrue(!facadeSource.contains("FALLBACK_HOT"),
                "search discovery must not synthesize static hot/suggest terms when no governed source exists");
        assertTrue(facadeSource.contains("return List.of();"),
                "empty suggestions must degrade clearly instead of returning fake fallback terms");
        assertTrue(postMapperSource.contains("searchPublicPostsFallback"), "post mapper must expose DB-side filtered fallback search");
        assertTrue(postMapperSource.contains("e.company LIKE CONCAT('%', #{term}, '%')"), "keyword fallback search must include company metadata");
        assertTrue(postMapperSource.contains("e.position LIKE CONCAT('%', #{term}, '%')"), "keyword fallback search must include position metadata");
        assertTrue(postMapperSource.contains("t.tag_name LIKE CONCAT('%', #{term}, '%')"), "keyword fallback search must include post tags");
        assertTrue(postMapperSource.contains("CASE WHEN") && postMapperSource.contains("#{minimumKeywordMatches}"),
                "fallback must calculate the normalized term-match threshold in SQL");
        assertTrue(postMapperSource.contains("t.tag_status = 1"), "governed fallback search must only recall active tags");
        assertTrue(postMapperSource.contains("t.merge_target_id IS NULL"), "governed fallback search must not recall merged tags");
        assertTrue(postMapperSource.contains("t.tag_name LIKE CONCAT('%', #{prefix}, '%')"), "suggest fallback search must include post tags");
        assertTrue(postMapperSource.contains("e.company LIKE CONCAT('%', #{company}, '%')"), "company filter must run in SQL before loading rows");
        assertTrue(postMapperSource.contains("e.position = #{position}"), "position filter must run in SQL before loading rows");
        assertTrue(postMapperSource.contains("LIMIT #{limit}"), "fallback SQL must be limit-bound");
        assertTrue(facadeSource.contains("tagMapper.selectHotTags(limit)")
                        && facadeSource.contains("tagMapper.selectHotTagsCompat(limit)"),
                "hot keyword lookup must use bounded tag mapper queries");
        assertTrue(tagMapperSource.contains("List<TagPO> selectHotTags(@Param(\"limit\") int limit)")
                        && tagMapperSource.contains("List<TagPO> selectHotTagsCompat(@Param(\"limit\") int limit)"),
                "tag mapper must expose governed and compatibility hot-tag queries");
        assertTrue(tagMapperSource.contains("ORDER BY use_count DESC, is_official DESC, id ASC")
                        && tagMapperSource.contains("LIMIT #{limit}"),
                "hot-tag SQL must preserve Java sorting semantics and apply the limit in the database");
    }
}
