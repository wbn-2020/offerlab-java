package com.offerlab.community.interaction.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractionSecurityGuardTest {

    @Test
    void contactRequestsMustFailClosedOnReviewModeration() throws Exception {
        String service = read("src/main/java/com/offerlab/community/interaction/application/ContactRequestService.java");

        assertContains(service, "ContentModerationService.ModerationDecision moderationDecision = contentModerationService.checkContent");
        assertContains(service, "if (moderationDecision.reviewRequired())");
        assertContains(service, "contact request requires review");
    }

    @Test
    void interactionWriteEndpointsMustBeRateLimited() throws Exception {
        String controller = read("src/main/java/com/offerlab/community/interaction/controller/InteractionController.java");

        assertContains(controller, "@PostMapping(\"/users/me/favorite-folders\")\n    @RateLimit");
        assertContains(controller, "@PutMapping(\"/users/me/favorite-folders/{folderId}\")\n    @RateLimit");
        assertContains(controller, "@PostMapping(\"/users/me/favorite-folders/reorder\")\n    @RateLimit");
        assertContains(controller, "@DeleteMapping(\"/users/me/favorite-folders/{folderId}\")\n    @RateLimit");
        assertContains(controller, "@PutMapping(\"/users/me/favorites/{postId}/folder\")\n    @RateLimit");
        assertContains(controller, "@PostMapping(\"/posts/{postId}/discussion-follow\")\n    @RateLimit");
        assertContains(controller, "@DeleteMapping(\"/posts/{postId}/discussion-follow\")\n    @RateLimit");
        assertContains(controller, "@PostMapping(\"/comments/admin/reports/{reportId}/review\")\n    @RateLimit");
        assertContains(controller, "@PostMapping(\"/posts/{postId}/comments/{commentId}/pin\")\n    @RateLimit");
        assertContains(controller, "@DeleteMapping(\"/posts/{postId}/comments/{commentId}/pin\")\n    @RateLimit");
        assertContains(controller, "@PostMapping(\"/comments/{commentId}/featured\")\n    @RateLimit");
        assertContains(controller, "@DeleteMapping(\"/comments/{commentId}/featured\")\n    @RateLimit");
        assertContains(controller, "@PostMapping(\"/comments/{commentId}/fold\")\n    @RateLimit");
        assertContains(controller, "@DeleteMapping(\"/comments/{commentId}/fold\")\n    @RateLimit");
    }

    @Test
    void commentPreviewMustExposeTotalReplyContract() throws Exception {
        String dto = read("src/main/java/com/offerlab/community/interaction/api/dto/CommentDTO.java");
        String mapper = read("src/main/java/com/offerlab/community/interaction/infrastructure/persistence/mapper/CommentMapper.java");
        String facade = read("src/main/java/com/offerlab/community/interaction/application/InteractionFacadeImpl.java");

        assertContains(dto, "private Integer replyCount");
        assertContains(dto, "private Boolean hasMoreReplies");
        assertContains(mapper, "countRepliesByRootIds");
        assertContains(facade, "countRepliesByRootIds(postId, rootIds)");
        assertContains(facade, "dto.setReplyCount");
        assertContains(facade, "dto.setHasMoreReplies");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static void assertContains(String source, String expected) {
        assertTrue(source.contains(expected), "Expected source to contain: " + expected);
    }
}
