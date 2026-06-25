package com.offerlab.community.post.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainConfigGuardTest {

    @Test
    void stageOneDomainConfigFoundationMustExist() throws Exception {
        String migration = read("../db/migration/20260623_domain_config.sql");
        String initSql = read("../db/init/11_governance.sql");
        String dto = read("src/main/java/com/offerlab/community/post/api/dto/DomainConfigDTO.java");
        String updateCmd = read("src/main/java/com/offerlab/community/post/api/dto/DomainConfigUpdateCmd.java");
        String po = read("src/main/java/com/offerlab/community/post/infrastructure/persistence/po/DomainConfigPO.java");
        String mapper = read("src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/DomainConfigMapper.java");
        String service = read("src/main/java/com/offerlab/community/post/application/DomainConfigService.java");
        String controller = read("src/main/java/com/offerlab/community/post/controller/DomainConfigController.java");
        String postService = read("src/main/java/com/offerlab/community/post/application/PostApplicationService.java");
        String migrationCheck = read("../community-infrastructure/src/main/java/com/offerlab/community/infra/db/MigrationCheckService.java");
        String schemaReadiness = read("../scripts/check-schema-readiness.mjs");

        assertContains(migration, "t_domain_config");
        assertContains(migration, "domain_name");
        assertContains(migration, "domain_slug");
        assertContains(migration, "risk_level");
        assertContains(migration, "posting_notice");
        assertContains(migration, "browse_notice");
        assertContains(migration, "interaction_notice");
        assertContains(migration, "PRIMARY KEY");
        assertContains(migration, "INSERT INTO t_domain_config");

        assertContains(initSql, "t_domain_config");
        assertContains(initSql, "domain_slug");

        assertContains(dto, "private Integer domain");
        assertContains(dto, "private String domainName");
        assertContains(dto, "private String domainSlug");
        assertContains(dto, "private String riskLevel");
        assertContains(updateCmd, "private Boolean enabled");
        assertContains(updateCmd, "private String postingNotice");

        assertContains(po, "t_domain_config");
        assertContains(mapper, "tableExists()");
        assertContains(mapper, "selectPublicList");
        assertContains(mapper, "selectAdminList");
        assertContains(mapper, "updateDomainConfig");

        assertContains(service, "listPublic()");
        assertContains(service, "listAdmin()");
        assertContains(service, "updateDomainConfig");
        assertContains(service, "defaultConfigs()");
        assertContains(service, "reviewRequiredForPublish");
        assertContains(service, "requireDomainEnabled");
        assertContains(service, "AdminAuditService");

        assertContains(controller, "@RequestMapping(\"/api/v1\")");
        assertContains(controller, "@GetMapping(\"/domains\")");
        assertContains(controller, "@GetMapping(\"/admin/domains\")");
        assertContains(controller, "@PutMapping(\"/admin/domains/{domain}\")");

        assertContains(postService, "domainConfigService.requireDomainEnabled(domain)");
        assertContains(postService, "domainConfigService.reviewRequiredForPublish(domain)");

        assertContains(migrationCheck, "\"t_domain_config\"");
        assertContains(migrationCheck, "domainConfigReady()");
        assertContains(schemaReadiness, "'t_domain_config'");
        assertContains(schemaReadiness, "20260623_domain_config.sql");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static void assertContains(String source, String expected) {
        assertTrue(source.contains(expected), "Expected source to contain: " + expected);
    }
}
