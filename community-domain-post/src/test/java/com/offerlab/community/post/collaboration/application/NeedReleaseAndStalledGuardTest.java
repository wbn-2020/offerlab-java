package com.offerlab.community.post.collaboration.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeedReleaseAndStalledGuardTest {

    @Test
    void releaseMustBeClaimantOnlyAndStalledMustRemainReadOnly() throws Exception {
        String controller = read("src/main/java/com/offerlab/community/post/collaboration/controller/CollaborationController.java");
        String service = read("src/main/java/com/offerlab/community/post/collaboration/application/CollaborationService.java");
        String mapper = read("src/main/java/com/offerlab/community/post/collaboration/infrastructure/persistence/CollaborationMapper.java");
        String stalledMethod = slice(
                service,
                "private static boolean isNeedStalled(",
                "private static LocalDateTime effectiveNeedLastProgressAt(");
        String migration = Files.readString(
                Path.of("../db/migration/20260718_collab_need_lifecycle.sql").normalize(),
                StandardCharsets.UTF_8);

        assertContains(controller, "@PostMapping(\"/needs/{needId}/release\")");
        assertContains(controller, "'collaboration:need:release:' + #uid");
        assertContains(controller, "service.releaseNeed(needId, cmd, UserContext.require())");

        assertContains(service, "only the current claimant can release this content need");
        assertContains(service, "if (!\"CLAIMED\".equals(need.getStatus()))");
        assertContains(mapper, "AND need_status = 'CLAIMED'");
        assertContains(mapper, "AND claimed_by_uid = #{uid}");
        assertContains(service, "\"RELEASED\"");
        assertContains(service, "need.getStatus(), \"OPEN\"");

        for (String assignment : new String[]{
                "need_status = 'OPEN'",
                "claimed_by_uid = NULL",
                "claimed_at = NULL",
                "last_progress_at = NULL",
                "submitted_by_uid = NULL",
                "submitted_at = NULL",
                "submission_resolution_type = NULL",
                "submission_resolution_id = NULL",
                "submission_note = NULL",
                "reject_reason = NULL"}) {
            assertContains(mapper, assignment);
        }

        assertContains(mapper, "claimed_at = CURRENT_TIMESTAMP(3)");
        assertContains(mapper, "last_progress_at = CURRENT_TIMESTAMP(3)");
        assertContains(service, "private static final long CLAIM_STALE_AFTER_DAYS = 14");
        assertContains(stalledMethod, "\"CLAIMED\".equals(row.getStatus())");
        assertContains(stalledMethod, "LocalDateTime lastProgressAt = effectiveNeedLastProgressAt(row)");
        assertContains(service, "return row.getUpdateTime()");
        assertContains(stalledMethod,
                "lastProgressAt.isBefore(LocalDateTime.now().minusDays(CLAIM_STALE_AFTER_DAYS))");
        assertContains(migration, "UPDATE t_collab_content_need");
        assertContains(migration, "WHERE need_status IN ('CLAIMED', 'SUBMITTED')");
        assertContains(migration, "COALESCE(claimed_at, update_time, create_time)");
        assertContains(migration, "COALESCE(last_progress_at, update_time, create_time)");
        assertFalse(migration.contains("INSERT INTO t_collab_content_need_event"),
                "The lifecycle migration must not fabricate pre-migration timeline events.");
        assertFalse(stalledMethod.contains("\"SUBMITTED\".equals(row.getStatus())"),
                "SUBMITTED needs must never be marked stalled.");
        assertFalse(service.contains("@Scheduled") || service.contains("autoRelease"),
                "Stalled detection must not introduce automatic release.");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static void assertContains(String source, String expected) {
        assertTrue(source.contains(expected), () -> "Expected source to contain: " + expected);
    }

    private static String slice(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        assertTrue(startIndex >= 0 && endIndex > startIndex,
                () -> "Expected source section: " + start + " ... " + end);
        return source.substring(startIndex, endIndex);
    }
}
