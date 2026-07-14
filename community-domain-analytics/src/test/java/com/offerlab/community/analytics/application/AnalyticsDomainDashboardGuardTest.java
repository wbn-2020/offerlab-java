package com.offerlab.community.analytics.application;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class AnalyticsDomainDashboardGuardTest {

    @Test
    public void testTrendDashboardMustSupportOptionalDomainAggregation() throws Exception {
        String facadeApiSource = read("src/main/java/com/offerlab/community/analytics/api/AnalyticsFacade.java");
        String controllerSource = read("src/main/java/com/offerlab/community/analytics/controller/AnalyticsController.java");
        String facadeSource = read("src/main/java/com/offerlab/community/analytics/application/AnalyticsFacadeImpl.java");
        String postMapperSource = read("../community-domain-post/src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/PostMapper.java");
        String tagMapperSource = read("../community-domain-post/src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/TagMapper.java");
        String initPostSql = read("../db/init/02_post.sql");
        String domainIndexMigration = read("../db/migration/20260618_post_extension_domain_index.sql");
        String schemaReadiness = read("../scripts/check-schema-readiness.mjs");
        String migrationCheckService = read("../community-infrastructure/src/main/java/com/offerlab/community/infra/db/MigrationCheckService.java");

        assertContains(facadeApiSource, "getTrendDashboard(String range, Integer domain)");

        assertContains(controllerSource, "@RequestParam(required = false) Integer domain");
        assertContains(controllerSource, "getTrendDashboard(period == null ? range : period, requireOptionalDomain(domain))");

        assertContains(facadeSource, "normalizeDomain(domain)");
        assertContains(facadeSource, "activeDomain");
        assertContains(facadeSource, "domainDistribution");
        assertContains(facadeSource, "domainHotContent");
        assertContains(facadeSource, "domainComparison");
        assertContains(facadeSource, "buildDomainComparison(since, allDomainTotal)");
        assertContains(facadeSource, "postCount");
        assertContains(facadeSource, "featuredCount");
        assertContains(facadeSource, "activeAuthors");
        assertContains(facadeSource, "topTags");
        assertContains(facadeSource, "hotContent");
        assertContains(facadeSource, "tagMapper.countTopTags(since, 10, activeDomain)");
        assertContains(facadeSource, "labelDomains(postMapper.countDomainDistribution(since))");
        assertContains(facadeSource, "PostDomain.fromCode(code).getDisplayName()");
        assertContains(facadeSource, "PostDomain.isValid(asInteger(row.get(\"name\")))");
        assertContains(facadeSource, "postMapper.listDomainComparisonStats(since)");
        assertContains(facadeSource, "tagMapper.countTopTagsByDomain(since, 5)");
        assertContains(facadeSource, "postMapper.listDomainHotContentByDomain(since, 5)");
        assertFalse(facadeSource.contains("postMapper.countPublishedSince(since, domainCode)"),
                "domain comparison must not issue one countPublishedSince query per domain");
        assertFalse(facadeSource.contains("postMapper.countFeaturedPostsSince(since, domainCode)"),
                "domain comparison must not issue one countFeaturedPostsSince query per domain");
        assertFalse(facadeSource.contains("postMapper.countActiveAuthorsSince(since, domainCode)"),
                "domain comparison must not issue one countActiveAuthorsSince query per domain");
        assertFalse(facadeSource.contains("tagMapper.countTopTags(since, 5, domainCode)"),
                "domain comparison must batch-load top tags by domain");
        assertFalse(facadeSource.contains("postMapper.listDomainHotContent(since, 5, domainCode)"),
                "domain comparison must batch-load hot content by domain");

        assertContains(postMapperSource, "countPublishedByDate(@Param(\"since\") LocalDateTime since, @Param(\"domain\") Integer domain)");
        assertContains(postMapperSource, "countDomainDistribution(@Param(\"since\") LocalDateTime since)");
        assertContains(postMapperSource, "listDomainHotContent(@Param(\"since\") LocalDateTime since, @Param(\"limit\") int limit, @Param(\"domain\") Integer domain)");
        assertContains(postMapperSource, "listDomainComparisonStats(@Param(\"since\") LocalDateTime since)");
        assertContains(postMapperSource, "listDomainHotContentByDomain(@Param(\"since\") LocalDateTime since, @Param(\"limitPerDomain\") int limitPerDomain)");
        assertContains(postMapperSource, "SELECT e.domain AS name");
        assertFalse(postMapperSource.contains("COALESCE(e.domain, 1)"),
                "unclassified posts must not be reassigned to the technology domain");
        assertFalse(postMapperSource.contains("JSON_EXTRACT(e.ext_json, '$.domain')"),
                "domain predicates and grouped analytics must use t_post_extension.domain instead of JSON extraction");
        assertFalse(postMapperSource.contains("JSON_EXTRACT(e_domain.ext_json, '$.domain')"),
                "domain list filters must use t_post_extension.domain instead of JSON extraction");
        assertContains(postMapperSource, "<if test=\"domain != null\">");

        assertContains(tagMapperSource, "countTopTags(@Param(\"since\") java.time.LocalDateTime since,");
        assertContains(tagMapperSource, "countTopTagsByDomain(@Param(\"since\") java.time.LocalDateTime since,");
        assertContains(tagMapperSource, "@Param(\"domain\") Integer domain");
        assertContains(tagMapperSource, "SELECT e.domain AS domain");
        assertFalse(tagMapperSource.contains("COALESCE(e.domain, 1)"),
                "unclassified tag rows must not be reassigned to the technology domain");
        assertFalse(tagMapperSource.contains("JSON_EXTRACT(e.ext_json, '$.domain')"),
                "tag analytics domain filters must use t_post_extension.domain instead of JSON extraction");

        assertContains(initPostSql, "domain          TINYINT      GENERATED ALWAYS AS");
        assertContains(initPostSql, "KEY idx_post_extension_domain_post (domain, post_id)");
        assertContains(domainIndexMigration, "v20260618_add_post_extension_domain_column_if_missing");
        assertContains(domainIndexMigration, "ADD COLUMN domain TINYINT GENERATED ALWAYS AS");
        assertContains(domainIndexMigration, "idx_post_extension_domain_post");
        assertContains(schemaReadiness, "...columns('t_post_extension', [");
        assertContains(schemaReadiness, "domain");
        assertContains(schemaReadiness, "idx_post_extension_domain_post");
        assertContains(migrationCheckService, "t_post_extension.domain");
        assertContains(migrationCheckService, "t_post_extension.idx_post_extension_domain_post");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static void assertContains(String source, String expected) {
        if (!source.contains(expected)) {
            throw new AssertionError("Expected source to contain: " + expected);
        }
    }
}
