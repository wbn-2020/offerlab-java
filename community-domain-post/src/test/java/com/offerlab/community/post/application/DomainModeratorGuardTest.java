package com.offerlab.community.post.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainModeratorGuardTest {
    private static final Path ROOT = Path.of("").toAbsolutePath();

    private static String read(String relative) throws Exception {
        return Files.readString(ROOT.resolve(relative), StandardCharsets.UTF_8);
    }

    private static void assertContains(String source, String needle, String message) {
        assertTrue(source.contains(needle), message + " Missing: " + needle);
    }

    @Test
    void domainModeratorGovernanceContractIsImplemented() throws Exception {
        String migration = read("../db/migration/20260617_domain_moderators.sql");
        String dto = read("src/main/java/com/offerlab/community/post/api/dto/DomainModeratorDTO.java");
        String po = read("src/main/java/com/offerlab/community/post/infrastructure/persistence/po/DomainModeratorPO.java");
        String mapper = read("src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/DomainModeratorMapper.java");
        String service = read("src/main/java/com/offerlab/community/post/application/DomainModeratorService.java");
        String controller = read("src/main/java/com/offerlab/community/post/controller/PostController.java");
        String reportService = read("src/main/java/com/offerlab/community/post/application/PostReportService.java");
        String featuredService = read("src/main/java/com/offerlab/community/post/application/PostFeaturedService.java");
        String knowledgeService = read("src/main/java/com/offerlab/community/post/application/PostKnowledgeReviewService.java");
        String reportMapper = read("src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/PostReportMapper.java");
        String opsController = read("../community-domain-search/src/main/java/com/offerlab/community/search/controller/OpsController.java");
        String migrationCheck = read("../community-infrastructure/src/main/java/com/offerlab/community/infra/db/MigrationCheckService.java");
        String schemaReadiness = read("../scripts/check-schema-readiness.mjs");

        assertContains(migration, "t_domain_moderator", "migration must create domain moderator table");
        assertContains(migration, "domain", "migration must store moderator domain");
        assertContains(migration, "created_by", "migration must track creator");
        assertContains(migration, "uk_domain_moderator_uid_domain", "migration must prevent duplicate active scope rows");
        assertContains(migration, "v20260617_add_column_if_missing", "migration must recover half-created table columns");
        assertContains(migration, "v20260617_add_index_if_missing", "migration must recover half-created table indexes");

        assertContains(dto, "private Long uid", "DTO must expose moderator uid");
        assertContains(dto, "private Integer domain", "DTO must expose domain");
        assertContains(dto, "private Boolean enabled", "DTO must expose enabled status");
        assertContains(po, "t_domain_moderator", "PO must bind table");
        assertContains(mapper, "selectByDomain", "mapper must list moderators by domain");
        assertContains(mapper, "upsertModerator", "mapper must upsert moderators");
        assertContains(mapper, "updateModeratorStatus", "mapper must enable/disable moderators");

        assertContains(service, "canModerateDomain", "service must expose scope check");
        assertContains(service, "requireModerateDomain", "service must enforce scope check");
        assertContains(service, "AdminPermissionService", "service must preserve admin/content moderator behavior");
        assertContains(service, "AdminAuditService", "service must audit moderator assignments");
        assertContains(service, "Post.DOMAIN_", "service must validate known domains");
        int globalGrant = service.indexOf("adminPermissionService.isAdmin(uid)");
        int domainValidation = service.indexOf("Integer normalizedDomain = requireKnownDomain(domain)");
        assertTrue(globalGrant >= 0 && domainValidation > globalGrant,
                "admin/content/local-open moderators must be allowed before null-domain validation");

        assertContains(controller, "/admin/domain-moderators", "controller must expose domain moderator endpoints");
        assertContains(controller, "domainModeratorService", "controller must use domain moderator service");
        assertContains(controller, "requireAdmin", "only admin can appoint/revoke domain moderators");
        assertContains(controller, "domainModeratorService.requireModerateDomain", "governance actions must enforce domain scope");

        assertContains(reportService, "listRecent(Integer status, Integer domain", "reports must support domain filtering");
        assertContains(reportService, "domainModeratorService.requireModerateDomain", "report review must enforce domain scope");
        assertContains(reportMapper, "COALESCE(e.domain, 1) = #{domain}", "report query must filter by indexed post domain");
        assertContains(featuredService, "domainModeratorService.requireModerateDomain", "featured updates must enforce domain scope");
        assertContains(knowledgeService, "domainModeratorService.requireModerateDomain", "knowledge review must enforce domain scope");
        assertContains(opsController, "domainModerator", "permissions endpoint must expose domain moderator flag");
        assertContains(opsController, "moderatedDomains", "permissions endpoint must expose moderated domains");
        assertContains(migrationCheck, "\"t_domain_moderator\"", "runtime readiness must check domain moderator table");
        assertContains(migrationCheck, "t_domain_moderator.uk_domain_moderator_uid_domain", "runtime readiness must check domain moderator unique scope index");
        assertContains(migrationCheck, "domainModeratorReady", "runtime readiness must expose a domain moderator readiness helper");
        assertContains(migrationCheck, "primaryKeyExists(\"t_domain_moderator\", \"id\")", "runtime readiness must verify domain moderator id is the primary key");
        assertContains(schemaReadiness, "'t_domain_moderator'", "script readiness must check domain moderator table");
        assertContains(schemaReadiness, "uk_domain_moderator_uid_domain", "script readiness must check domain moderator unique scope index");
        assertContains(schemaReadiness, "...primaryKeys('t_domain_moderator', ['id'])", "script readiness must verify domain moderator id is the primary key");
        assertContains(schemaReadiness, "20260617_domain_moderators.sql", "script readiness must map missing domain moderator items to migration");
        assertContains(migrationCheck, "t_post_extension.idx_post_extension_domain_post", "runtime readiness must check the post extension domain index");
        assertContains(schemaReadiness, "idx_post_extension_domain_post", "script readiness must check the post extension domain index");
    }
}
