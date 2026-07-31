package com.offerlab.community.post.collaboration.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeedClaimCycleRevisionGuardTest {

    @Test
    void claimCyclesAndRevisionsMustBeAdditivePrivateAndDualWritten() throws Exception {
        String service = read("src/main/java/com/offerlab/community/post/collaboration/application/CollaborationService.java");
        String mapper = read("src/main/java/com/offerlab/community/post/collaboration/infrastructure/persistence/CollaborationMapper.java");
        String models = read("src/main/java/com/offerlab/community/post/collaboration/api/CollaborationModels.java");
        String rows = read("src/main/java/com/offerlab/community/post/collaboration/infrastructure/persistence/CollaborationRows.java");
        String migration = readMigration("20260719_collab_need_claim_cycle_revision.sql");
        String sync = read("../db/migration/sync-flyway-resources.ps1");
        String readiness = read("../scripts/check-schema-readiness.mjs");
        String runtimeReadiness = read(
                "../community-infrastructure/src/main/java/com/offerlab/community/infra/db/MigrationCheckService.java");

        for (String table : new String[]{
                "t_collab_content_need_claim_cycle",
                "t_collab_content_need_revision"}) {
            assertContains(migration, "CREATE TABLE IF NOT EXISTS " + table);
            assertContains(mapper, table);
            assertContains(readiness, "'" + table + "'");
            assertContains(runtimeReadiness, "\"" + table + "\"");
        }

        for (String operation : new String[]{
                "insertNeedClaimCycle",
                "insertNeedRevision",
                "decideNeedRevision",
                "endNeedClaimCycle",
                "touchNeedClaimCycle"}) {
            assertContains(service, "mapper." + operation);
        }
        for (String eventType : new String[]{
                "CLAIMED", "SUBMITTED", "REJECTED", "WITHDRAWN",
                "ACCEPTED", "RELEASED", "CLOSED"}) {
            assertContains(service, "\"" + eventType + "\"");
        }

        assertContains(service, "currentClaimCycleNo");
        assertContains(service, "currentRevisionNo");
        assertContains(service, "toNeedClaimCycles");
        assertContains(service, "canViewParticipantDetails");
        assertContains(mapper, "n.claimed_by_uid = #{viewerUid}");
        assertContains(mapper, "c.claimant_uid = #{viewerUid}");
        assertContains(mapper, "r.visibility_scope = 'PUBLIC'");
        assertContains(models, "List<NeedClaimCycleDTO> claimCycles");
        assertContains(models, "public static class NeedClaimCycleDTO");
        assertContains(models, "public static class NeedRevisionDTO");
        assertContains(rows, "public static class NeedClaimCycleRow");
        assertContains(rows, "public static class NeedRevisionRow");

        assertFalse(migration.matches("(?is).*UPDATE\\s+t_collab_content_need\\s+.*"),
                "The migration must not mutate the current need projection.");
        assertFalse(migration.matches("(?is).*INSERT\\s+INTO\\s+t_collab_content_need\\s*\\(.*"),
                "The migration must not fabricate a new current need.");
        assertFalse(migration.matches("(?is).*FOREIGN\\s+KEY.*"),
                "Claim history must follow the repository's additive, no-cross-table-FK migration convention.");
        assertContains(migration, "cycle_status = 'ACTIVE'");
        assertContains(migration, "uk_collab_need_claim_cycle_active");
        assertContains(migration, "cycle_origin");
        assertContains(migration, "LEGACY_CURRENT");
        assertContains(migration, "revision_origin");
        assertContains(migration, "visibility_scope");
        assertContains(migration, "revision_no");

        assertContains(sync, "20260719_collab_need_claim_cycle_revision.sql");
        assertContains(sync, "21_collab_need_claim_cycle_revision.sql");
        assertContains(readiness, "20260719_collab_need_claim_cycle_revision.sql");
        assertContains(runtimeReadiness, "20260719_collab_need_claim_cycle_revision.sql");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path).normalize(), StandardCharsets.UTF_8);
    }

    private static String readMigration(String file) throws Exception {
        return Files.readString(Path.of("../db/migration", file).normalize(), StandardCharsets.UTF_8);
    }

    private static void assertContains(String source, String expected) {
        assertTrue(source.contains(expected), () -> "Expected source to contain: " + expected);
    }
}
