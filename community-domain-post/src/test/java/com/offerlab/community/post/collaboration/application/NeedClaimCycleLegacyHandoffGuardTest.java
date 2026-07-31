package com.offerlab.community.post.collaboration.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeedClaimCycleLegacyHandoffGuardTest {

    @Test
    void migrationAndFirstActionMustUseExplicitLegacyOriginsWithoutFabricatingHistory() throws Exception {
        String migration = read("../db/migration/20260719_collab_need_claim_cycle_revision.sql");
        String service = read("src/main/java/com/offerlab/community/post/collaboration/application/CollaborationService.java");
        String mapper = read(
                "src/main/java/com/offerlab/community/post/collaboration/infrastructure/persistence/"
                        + "CollaborationMapper.java");
        String rows = read(
                "src/main/java/com/offerlab/community/post/collaboration/infrastructure/persistence/"
                        + "CollaborationRows.java");
        String models = read("src/main/java/com/offerlab/community/post/collaboration/api/CollaborationModels.java");
        String init = read("../db/init/21_collab_need_claim_cycle_revision.sql");
        String flyway = read(
                "../community-bootstrap/src/main/resources/db/flyway/core/"
                        + "V20260719.01__collab_need_claim_cycle_revision.sql");

        assertEquals(migration, init, "init schema must mirror the canonical migration");
        assertEquals(migration, flyway, "Flyway resource must mirror the canonical migration");
        assertTrue(migration.contains("cycle_origin"));
        assertTrue(migration.contains("'LEGACY_CURRENT'"));
        assertTrue(migration.contains("n.need_status IN ('CLAIMED', 'SUBMITTED')"));
        assertTrue(migration.contains("n.claimed_by_uid IS NOT NULL"));
        assertTrue(migration.contains("COALESCE(n.claimed_at, n.update_time, n.create_time)"));
        assertTrue(migration.contains("NOT EXISTS"));
        assertTrue(migration.contains("revision_origin"));
        assertTrue(migration.contains("LEGACY_CURRENT_SUBMISSION"));
        assertFalse(migration.matches("(?is).*INSERT\\s+INTO\\s+t_collab_content_need_revision\\s*\\(.*"),
                "migration must not invent a historical revision row");

        assertTrue(service.contains("ensureCurrentClaimCycle"));
        assertTrue(service.contains("currentSubmittedRevisionOrHandoff"));
        assertTrue(service.contains("insertLegacyCurrentNeedClaimCycle"));
        assertTrue(service.contains("LEGACY_CURRENT_SUBMISSION"));
        assertTrue(service.contains("\"SUBMISSION\", null"));
        assertTrue(service.contains("current collaboration claim cycle does not match the claimant"));

        String handoff = methodBody(service,
                "private NeedRevisionRow currentSubmittedRevisionOrHandoff(");
        assertTrue(handoff.contains("String note = clean(need.getSubmissionNote(), 1000)"));
        assertTrue(handoff.contains("\"LEGACY_CURRENT_SUBMISSION\""));
        assertTrue(handoff.contains("need.getSubmittedAt())"),
                "unknown legacy submission time must be left for the database to timestamp now");
        assertFalse(handoff.contains("effectiveNeedLastProgressAt(need)"),
                "last progress is not proof of an old submission timestamp");
        assertFalse(handoff.contains("LEGACY_CURRENT_SUBMISSION:"),
                "migration provenance belongs in revision_origin, not in the user note");

        for (String method : new String[]{
                "submitNeed", "acceptNeed", "rejectNeed", "withdrawNeed", "closeNeed", "releaseNeed"}) {
            String action = methodBody(service, "public NeedDTO " + method + "(");
            assertTrue(action.contains("ensureCurrentClaimCycle"),
                    () -> method + " must attach to the current claim cycle");
        }
        for (String method : new String[]{"acceptNeed", "rejectNeed", "withdrawNeed", "closeNeed"}) {
            assertTrue(methodBody(service, "public NeedDTO " + method + "(")
                            .contains("currentSubmittedRevisionOrHandoff"),
                    () -> method + " must hand off a legacy current submission before deciding it");
        }
        assertTrue(methodBody(service, "public NeedDTO submitNeed(").contains("insertNeedRevision"));
        assertTrue(methodBody(service, "public NeedDTO releaseNeed(").contains("endClaimCycle"));

        assertTrue(mapper.contains("cycle_origin AS cycleOrigin"));
        assertTrue(mapper.contains("revision_origin AS revisionOrigin"));
        assertTrue(mapper.contains("insertLegacyCurrentNeedClaimCycle"));
        assertTrue(mapper.contains("'LEGACY_CURRENT'"));
        assertTrue(mapper.contains("#{revisionOrigin}"));
        assertTrue(mapper.contains("#{submittedAt}"));

        assertTrue(rows.contains("private String cycleOrigin"));
        assertTrue(rows.contains("private String revisionOrigin"));
        assertTrue(models.contains("private String currentClaimCycleOrigin"));
        assertTrue(models.contains("private String currentRevisionOrigin"));
        assertTrue(models.contains("private String cycleOrigin"));
        assertTrue(models.contains("private String revisionOrigin"));
    }

    private static String methodBody(String source, String marker) {
        int start = source.indexOf(marker);
        assertTrue(start >= 0, () -> "method marker not found: " + marker);
        int open = source.indexOf('{', start);
        assertTrue(open >= 0, () -> "method body not found: " + marker);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (ch == '{') {
                depth++;
            } else if (ch == '}' && --depth == 0) {
                return source.substring(open, i + 1);
            }
        }
        throw new AssertionError("unterminated method body: " + marker);
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path).normalize(), StandardCharsets.UTF_8);
    }
}
