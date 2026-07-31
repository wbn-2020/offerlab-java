package com.offerlab.community.post.collaboration.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MyClaimedNeedsGuardTest {

    @Test
    void myClaimedNeedsMustRemainAnAuthenticatedRateLimitedReadPath() throws Exception {
        String controller = read("src/main/java/com/offerlab/community/post/collaboration/controller/CollaborationController.java");
        String service = read("src/main/java/com/offerlab/community/post/collaboration/application/CollaborationService.java");
        String mapper = read("src/main/java/com/offerlab/community/post/collaboration/infrastructure/persistence/CollaborationMapper.java");
        String models = read("src/main/java/com/offerlab/community/post/collaboration/api/CollaborationModels.java");

        assertContains(controller, "@GetMapping(\"/needs/mine\")");
        assertContains(controller, "@RateLimit(key = \"'collaboration:needs:mine:' + #uid\"");
        assertContains(controller, "service.listMyClaimedNeeds(UserContext.require(), status, cursor, size)");

        assertContains(service, "public PageResult<NeedDTO> listMyClaimedNeeds");
        assertContains(service, "\"OPEN\", \"CLAIMED\", \"SUBMITTED\", \"COMPLETED\", \"CLOSED\", \"MERGED\"");
        assertContains(service, "mapper.listNeedsByClaimant(uid, activeStatus, safeCursor(cursor), pageSize + 1)");
        assertContains(service, "row -> toNeed(row, uid,");

        assertContains(mapper, "List<CollaborationRows.NeedRow> listNeedsByClaimant");
        assertContains(mapper, "WHERE n.claimed_by_uid = #{uid}");
        assertContains(mapper, "AND n.moderation_hidden = 0");
        assertContains(mapper, "AND (#{cursor} = 0 OR n.id &lt; #{cursor})");
        assertContains(mapper, "AND n.need_status = #{status}");
        assertContains(mapper, "ORDER BY n.id DESC");

        assertFalse(models.contains("claimedByMe"),
                "The current slice keeps claimant identity in claimedByUid and must not expand NeedDTO.");
        assertTrue(mapper.contains("idx_collab_need_claimed") || migrationContainsClaimedIndex(),
                "The claimant query must be backed by the existing idx_collab_need_claimed index.");
    }

    private static boolean migrationContainsClaimedIndex() throws Exception {
        Path migration = Path.of("../db/migration/20260714_collaboration_stage2.sql").normalize();
        return Files.readString(migration, StandardCharsets.UTF_8).contains(
                "idx_collab_need_claimed (claimed_by_uid, need_status, update_time, id)");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static void assertContains(String source, String expected) {
        assertTrue(source.contains(expected), () -> "Expected source to contain: " + expected);
    }
}
