package com.offerlab.community.post.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpertCertificationGuardTest {

    @Test
    void stageFourExpertCertificationFoundationMustStayCalibrated() throws Exception {
        String migration = read("../db/migration/20260624_expert_certification.sql");
        String mapper = read("src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/ExpertCertificationMapper.java");
        String service = read("src/main/java/com/offerlab/community/post/application/ExpertCertificationService.java");
        String controller = read("src/main/java/com/offerlab/community/post/controller/ExpertCertificationController.java");
        String migrationCheck = read("../community-infrastructure/src/main/java/com/offerlab/community/infra/db/MigrationCheckService.java");
        String schemaReadiness = read("../scripts/check-schema-readiness.mjs");

        assertContains(migration, "t_expert_cert_application");
        assertContains(migration, "active_guard");
        assertContains(migration, "uk_expert_cert_active_guard");
        assertContains(migration, "idx_expert_cert_applicant_domain");
        assertContains(migration, "idx_expert_cert_review_queue");

        assertContains(mapper, "acquireNamedLock");
        assertContains(mapper, "releaseNamedLock");
        assertContains(service, "migrationCheckService.expertCertificationReady()");
        assertContains(service, "acquireSubmitLock");
        assertContains(service, "releaseSubmitLock");
        assertContains(service, "offerlab:expert-cert:");

        assertContains(controller, "@GetMapping(\"/applications/me\")");
        assertContains(controller, "@PostMapping(\"/applications/{applicationId}/revoke\")");
        assertContains(controller, "@GetMapping(\"/admin/applications\")");

        assertContains(migrationCheck, "\"t_expert_cert_application\"");
        assertContains(migrationCheck, "expertCertificationReady()");
        assertContains(schemaReadiness, "'t_expert_cert_application'");
        assertContains(schemaReadiness, "20260624_expert_certification.sql");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static void assertContains(String source, String expected) {
        assertTrue(source.contains(expected), "Expected source to contain: " + expected);
    }
}
