package com.offerlab.community.post.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostContentEnvironmentContractGuardTest {

    @Test
    void schemaPersistencePublishAndPublicCacheMustShareTheEnvironmentContract() throws Exception {
        String migration = read("../db/migration/20260814_post_content_environment.sql");
        String init = read("../db/init/02_post.sql");
        String post = read("src/main/java/com/offerlab/community/post/domain/model/Post.java");
        String po = read("src/main/java/com/offerlab/community/post/infrastructure/persistence/po/PostPO.java");
        String repository = read("src/main/java/com/offerlab/community/post/infrastructure/persistence/PostRepositoryImpl.java");
        String service = read("src/main/java/com/offerlab/community/post/application/PostApplicationService.java");
        String facade = read("src/main/java/com/offerlab/community/post/application/PostFacadeImpl.java");
        String governanceApply = read("../docs/governance/content-environment/02-apply-approved-template.sql");
        String governanceProvision = read("../docs/governance/content-environment/00-provision-backup-table.sql");
        String governanceReadme = read("../docs/governance/content-environment/README.md");

        assertTrue(migration.contains("DEFAULT 'UNCLASSIFIED'"));
        assertTrue(!migration.contains("UPDATE t_post_main"),
                "schema migration must not classify historical data automatically");
        assertTrue(init.contains("content_environment VARCHAR(16) NOT NULL DEFAULT 'UNCLASSIFIED'"));
        assertTrue(post.contains("CONTENT_ENVIRONMENT_COMMUNITY = \"COMMUNITY\""));
        assertTrue(po.contains("private String contentEnvironment;"));
        assertTrue(repository.contains(".contentEnvironment(po.getContentEnvironment())"));
        assertTrue(repository.contains("po.setContentEnvironment(p.getContentEnvironment())"));
        assertTrue(service.contains(".contentEnvironment(Post.CONTENT_ENVIRONMENT_COMMUNITY)"),
                "normal publish must explicitly classify community content");
        assertTrue(facade.contains("Post.isCommunityContent(dto.getContentEnvironment())"),
                "cached detail DTO must fail closed when the environment is absent or stale");
        assertTrue(facade.contains("Post.isCommunityContent(post.getContentEnvironment())"),
                "public domain reads must require COMMUNITY");
        assertTrue(governanceApply.contains("content_environment_preflight_guard"),
                "approved classification must have SQL-level preflight assertions");
        assertTrue(governanceApply.contains("@expected_approved_count"),
                "approved classification must require an exact reviewed count");
        assertTrue(governanceApply.contains("decision_status <> 'APPROVED'"),
                "only fully approved rows may be classified");
        assertTrue(governanceApply.contains("no_unreviewed_unclassified_rows"),
                "the release classifier must reject partial historical classification");
        assertTrue(governanceApply.contains("historical_unclassified_is_zero"),
                "the release classifier must verify the final fail-closed backlog is empty");
        assertFalse(governanceApply.contains("CREATE TABLE IF NOT EXISTS t_post_content_environment_backup"),
                "apply must not create persistent tables before preflight");
        assertTrue(governanceApply.contains("approved_backup_table_is_preprovisioned"),
                "apply must stop before writes when the reviewed backup table is absent");
        assertTrue(governanceProvision.contains("CREATE TABLE IF NOT EXISTS t_post_content_environment_backup"),
                "backup-table provisioning must be independently reviewable");
        assertTrue(governanceApply.indexOf("START TRANSACTION")
                        > governanceApply.indexOf("no_unreviewed_unclassified_rows"),
                "all apply preflight assertions must precede persistent writes");
        assertTrue(governanceReadme.contains("preflight assertions stop before writes"),
                "runbook must document the hard-stop behavior");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
