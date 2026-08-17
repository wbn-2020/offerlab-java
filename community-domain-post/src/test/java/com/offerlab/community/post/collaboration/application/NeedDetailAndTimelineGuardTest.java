package com.offerlab.community.post.collaboration.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeedDetailAndTimelineGuardTest {

    @Test
    void detailTimelinePrivacyAndStateEventsMustRemainServerEnforced() throws Exception {
        String controller = read("src/main/java/com/offerlab/community/post/collaboration/controller/CollaborationController.java");
        String service = read("src/main/java/com/offerlab/community/post/collaboration/application/CollaborationService.java");
        String mapper = read("src/main/java/com/offerlab/community/post/collaboration/infrastructure/persistence/CollaborationMapper.java");
        String event = read("src/main/java/com/offerlab/community/post/collaboration/api/CollaborationNeedStateChangedEvent.java");
        String models = read("src/main/java/com/offerlab/community/post/collaboration/api/CollaborationModels.java");
        String publicTimelineEvent = extractClassBlock(models, "NeedEventDTO");
        String migration = readMigration("20260718_collab_need_lifecycle.sql");

        assertContains(controller, "@GetMapping(\"/needs/{needId}/events\")");
        assertContains(controller, "'public:collaboration:need-events:' + #needId");
        assertContains(controller, "service.listNeedEvents(needId, UserContext.get(), cursor, size)");

        assertContains(mapper, "ORDER BY id DESC");
        assertContains(mapper, "(#{cursor} = 0 OR e.id < #{cursor})");
        assertContains(mapper, "e.visibility_scope = 'PUBLIC'");
        assertContains(mapper, "e.visibility_scope = 'PARTICIPANTS'");
        assertContains(mapper, "e.visibility_scope = 'MANAGERS'");
        assertContains(mapper, "INNER JOIN t_collab_content_need n ON n.id = e.need_id");
        assertContains(mapper, "n.claimed_by_uid = #{viewerUid}");
        assertContains(mapper, "e.claimant_uid = #{viewerUid}");
        assertContains(mapper, "THEN e.note");
        assertContains(mapper, "THEN e.visibility_scope");
        assertFalse(mapper.contains("includeParticipants"),
                "Current claimant access must be evaluated atomically inside the event query.");
        assertFalse(mapper.contains("hasNeedParticipation"),
                "Historical claimants must not receive permanent access to later private timeline events.");
        assertFalse(service.contains("mapper.hasNeedParticipation"),
                "Timeline participant access must be based on the current claimant relationship.");
        assertContains(service, "int includeManagers = canManage ? 1 : 0");
        assertContains(service, "need.getId(), viewerUid, includeManagers");
        assertContains(service, "requireNonNegativeCursor(cursor)");
        assertContains(controller, "Result<NeedEventTimelineDTO> listNeedEvents");
        assertContains(service, "NeedEventTimelineDTO listNeedEvents");
        assertContains(service, "result.setHistoryIntegrityWarning(skippedEventCount > 0)");
        assertFalse(service.contains(".withDiagnostic("),
                "Public timelines must not expose internal reconciliation metrics through page diagnostics.");
        assertContains(service, "log.error(\"Collaboration need timeline integrity issues detected:");
        assertContains(mapper, "long countVisibleNeedEvents(");
        assertContains(mapper, "long countInvalidVisibleNeedEvents(");
        assertContains(mapper, "List<Long> listInvalidVisibleNeedEventIds(");
        assertContains(mapper, "AND NOT (");
        assertContains(mapper, "e.id > 0");
        assertContains(mapper, "TRIM(e.target_type) <> ''");
        assertContains(mapper, "e.create_time IS NOT NULL");
        assertContains(mapper, "e.event_type IN ('CREATED', 'CLAIMED', 'SUBMITTED', 'WITHDRAWN', 'REJECTED',");
        assertContains(service, "invalidEventIds");

        assertContains(service, ".note(row.getNote())");
        assertContains(service, ".hasActor(row.getActorUid() != null && row.getActorUid() > 0)");
        assertFalse(publicTimelineEvent.contains("private Long actorUid;"),
                "Public timeline DTOs must not expose actor uid values.");
        assertFalse(publicTimelineEvent.contains("private Long targetId;"),
                "Public timeline DTOs must not expose internal target ids.");
        assertContains(service, ".submissionNote(canViewParticipantDetails ? row.getSubmissionNote() : null)");
        assertContains(service, ".rejectReason(canViewParticipantDetails ? row.getRejectReason() : null)");
        assertContains(service, ".closedReason(canViewParticipantDetails ? row.getClosedReason() : null)");

        for (String eventType : new String[]{
                "CREATED", "CLAIMED", "SUBMITTED", "WITHDRAWN", "REJECTED",
                "ACCEPTED", "COMPLETED", "CLOSED", "MERGED", "RELEASED"}) {
            assertContains(service, "appendNeedEvent(");
            assertContains(service, "\"" + eventType + "\"");
        }
        assertContains(service, "String acceptanceNote = clean(cmd == null ? null : cmd.getNote(), 500)");
        assertContains(service, ".dedupKey(\"collaboration_need_event:\" + eventId)");
        assertContains(service, ".eventId(eventId)");

        for (String field : new String[]{
                "Long eventId", "Long needId", "String eventType", "Long actorUid",
                "Long creatorUid", "Long claimantUid", "Integer domain", "Long targetNeedId",
                "String targetType", "Long targetId", "String note", "String fromStatus",
                "String toStatus", "Long occurredAt", "String dedupKey"}) {
            assertContains(event, "private " + field + ";");
        }
        assertFalse(event.contains("List<"), "State events must not carry follower uid lists.");

        assertContains(migration, "ADD COLUMN claimed_at");
        assertContains(migration, "ADD COLUMN last_progress_at");
        assertContains(migration, "CREATE TABLE IF NOT EXISTS t_collab_content_need_event");
        assertContains(migration, "claimant_uid");
        assertContains(migration, "idx_collab_need_event_need (need_id, id)");
        assertContains(migration, "idx_collab_need_event_visibility (need_id, visibility_scope, id)");

        assertContains(service, "private static final int REQUIRED_TABLES = 18");
        assertContains(service, "private static final int REQUIRED_CRITICAL_COLUMNS = 28");
        assertContains(service, "db/migration/20260718_collab_need_lifecycle.sql");
        assertContains(mapper, "'t_collab_content_need_event'");
        assertContains(mapper, "column_name = 'claimed_at'");
        assertContains(mapper, "column_name = 'last_progress_at'");
        assertContains(mapper, "column_name = 'claimant_uid'");
        assertContains(mapper, "column_name = 'visibility_scope'");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static String readMigration(String file) throws Exception {
        return Files.readString(Path.of("../db/migration", file).normalize(), StandardCharsets.UTF_8);
    }

    private static void assertContains(String source, String expected) {
        assertTrue(source.contains(expected), () -> "Expected source to contain: " + expected);
    }

    private static String extractClassBlock(String source, String className) {
        String marker = "public static class " + className;
        int start = source.indexOf(marker);
        assertTrue(start >= 0, () -> "Expected class not found: " + className);
        int nextClass = source.indexOf("public static class ", start + marker.length());
        return nextClass < 0 ? source.substring(start) : source.substring(start, nextClass);
    }
}
