package com.offerlab.community.post.collaboration.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeedFollowLifecycleGuardTest {

    @Test
    void followedNeedsAndFollowerFacadeMustShareRelationIdKeysetSemantics() throws Exception {
        String controller = read("src/main/java/com/offerlab/community/post/collaboration/controller/CollaborationController.java");
        String service = read("src/main/java/com/offerlab/community/post/collaboration/application/CollaborationService.java");
        String mapper = read("src/main/java/com/offerlab/community/post/collaboration/infrastructure/persistence/CollaborationMapper.java");
        String facade = read(
                "src/main/java/com/offerlab/community/post/collaboration/api/CollaborationNeedFollowFacade.java");
        String migration = readMigration("20260718_collab_need_lifecycle.sql");

        assertContains(controller, "@GetMapping(\"/needs/mine/followed\")");
        assertContains(controller, "'collaboration:needs:mine:followed:' + #uid");
        assertContains(controller,
                "service.listMyFollowedNeeds(UserContext.require(), status, cursor, size)");

        assertContains(mapper, "WHERE f.uid = #{uid}");
        assertContains(mapper, "AND f.active = 1");
        assertContains(mapper, "AND n.moderation_hidden = 0");
        assertContains(mapper, "(#{cursor} = 0 OR f.id &lt; #{cursor})");
        assertContains(mapper, "ORDER BY f.id DESC");
        assertContains(service, "NeedRow::getFollowId");

        assertContains(mapper, "int reactivateNeedFollow");
        assertContains(mapper, "SET id = #{id}");
        assertContains(mapper, "AND active = 0");
        assertContains(mapper, "INSERT IGNORE INTO t_collab_content_need_follow");
        assertContains(service, "if (changed == 0)");
        assertContains(service, "if (changed == 1)");
        assertFalse(mapper.contains("ON DUPLICATE KEY UPDATE active = 1"),
                "Repeated active follows must not refresh the relation or increment follower_count.");

        assertContains(facade,
                "PageResult<Long> listActiveFollowerUids(Long needId, long cursor, int size)");
        assertContains(service,
                "public PageResult<Long> listActiveFollowerUids(Long needId, long cursor, int size)");
        assertContains(mapper, "List<CollaborationRows.NeedFollowRow> listActiveNeedFollowers");
        assertContains(mapper, "WHERE need_id = #{needId}");
        assertContains(mapper, "AND active = 1");
        assertContains(mapper, "(#{cursor} = 0 OR id &lt; #{cursor})");
        assertContains(service, "visible.stream().map(NeedFollowRow::getUid).toList()");
        assertContains(service, "visible.get(visible.size() - 1).getId()");

        assertContains(migration, "idx_collab_need_follow_uid_active_id (uid, active, id)");
        assertContains(migration, "idx_collab_need_follow_need_active_id (need_id, active, id)");
        assertFalse(migration.contains("idx_collab_need_follow_uid (uid, active, update_time, id)"),
                "The lifecycle migration must add relation-id indexes instead of copying the existing update-time index.");
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
}
