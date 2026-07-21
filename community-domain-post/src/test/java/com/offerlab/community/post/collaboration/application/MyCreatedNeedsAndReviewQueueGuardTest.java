package com.offerlab.community.post.collaboration.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MyCreatedNeedsAndReviewQueueGuardTest {

    @Test
    void creatorAndModeratorQueuesMustRemainServerFilteredAndRateLimited() throws Exception {
        String controller = read("src/main/java/com/offerlab/community/post/collaboration/controller/CollaborationController.java");
        String service = read("src/main/java/com/offerlab/community/post/collaboration/application/CollaborationService.java");
        String mapper = read("src/main/java/com/offerlab/community/post/collaboration/infrastructure/persistence/CollaborationMapper.java");

        assertContains(controller, "@GetMapping(\"/needs/mine/created\")");
        assertContains(controller, "'collaboration:needs:mine:created:' + #uid");
        assertContains(controller,
                "service.listMyCreatedNeeds(UserContext.require(), status, cursor, size)");

        assertContains(controller, "@GetMapping(\"/needs/review-queue\")");
        assertContains(controller, "'collaboration:needs:review-list:' + #uid");
        assertContains(controller,
                "service.listNeedReviewQueue(domain, UserContext.require(), cursor, size)");

        assertContains(service, "public PageResult<NeedDTO> listMyCreatedNeeds");
        assertContains(service,
                "mapper.listNeedsByCreator(uid, activeStatus, safeCursor(cursor), pageSize + 1)");
        assertContains(service, "public PageResult<NeedDTO> listNeedReviewQueue");
        assertContains(service,
                "adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR)");
        assertContains(service, "domainModeratorService.requireModerateDomain(uid, activeDomain)");
        assertContains(service, "mapper.listNeedReviewQueue(");

        assertContains(mapper, "List<CollaborationRows.NeedRow> listNeedsByCreator");
        assertContains(mapper, "WHERE n.creator_uid = #{uid}");
        assertContains(mapper, "List<CollaborationRows.NeedRow> listNeedReviewQueue");
        assertContains(mapper, "AND n.need_status = 'SUBMITTED'");
        assertTrue(count(mapper, "AND n.moderation_hidden = 0") >= 4,
                "All user and public need queries must continue filtering hidden rows.");
        assertTrue(count(mapper, "(#{cursor} = 0 OR n.id &lt; #{cursor})") >= 4,
                "Creator and review queues must preserve id keyset pagination.");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static long count(String source, String expected) {
        return source.lines().filter(line -> line.contains(expected)).count();
    }

    private static void assertContains(String source, String expected) {
        assertTrue(source.contains(expected), () -> "Expected source to contain: " + expected);
    }
}
