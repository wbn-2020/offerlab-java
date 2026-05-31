package com.offerlab.community.post.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PostModerationReviewGuardTest {

    @Test
    void reviewKeywordsMustPutPostsAndCommentsIntoPendingReview() throws Exception {
        String moderationService = read("../community-infrastructure/src/main/java/com/offerlab/community/infra/moderation/ContentModerationService.java");
        String postCreateCmd = read("src/main/java/com/offerlab/community/post/api/dto/PostCreateCmd.java");
        String postUpdateCmd = read("src/main/java/com/offerlab/community/post/api/dto/PostUpdateCmd.java");
        String postController = read("src/main/java/com/offerlab/community/post/controller/PostController.java");
        String postService = read("src/main/java/com/offerlab/community/post/application/PostApplicationService.java");
        String commentCmd = read("../community-domain-interaction/src/main/java/com/offerlab/community/interaction/api/dto/CommentCreateCmd.java");
        String interactionController = read("../community-domain-interaction/src/main/java/com/offerlab/community/interaction/controller/InteractionController.java");
        String interactionFacade = read("../community-domain-interaction/src/main/java/com/offerlab/community/interaction/application/InteractionFacadeImpl.java");

        assertTrue(moderationService.contains("record ModerationDecision"), "moderation service must expose a structured decision");
        assertTrue(moderationService.contains("public ModerationDecision checkContent"), "publish flows must be able to inspect REVIEW decisions");
        assertTrue(moderationService.contains("checkContent(uid, scope, values);"), "legacy requireContentAllowed calls must delegate to the structured checker");
        assertTrue(moderationService.contains("ModerationDecision.review(keyword.getKeyword(), contentSummary)"), "REVIEW hits must return a review-required decision");
        assertTrue(moderationService.contains("throw new BizException(ErrorCode.PARAM_ERROR.getCode()"), "BLOCK hits must still reject immediately");

        assertTrue(postCreateCmd.contains("private Boolean reviewRequired"), "post create command must carry reviewRequired");
        assertTrue(postUpdateCmd.contains("private Boolean reviewRequired"), "post update command must carry reviewRequired");
        assertTrue(postController.contains("contentModerationService.checkContent"), "post controller must use structured moderation decisions");
        assertTrue(postController.contains("reviewRequired(moderationDecision.reviewRequired())"), "post controller must pass REVIEW decision into application service");
        assertTrue(postController.contains("\"reviewRequired\", moderationDecision.reviewRequired()"), "post APIs must tell the frontend when content entered review");

        assertTrue(postService.contains("reviewRequired ? Post.STATUS_REVIEWING : Post.STATUS_PUBLISHED"), "new REVIEW posts must be saved as reviewing");
        assertTrue(postService.contains("post.setPostStatus(Post.STATUS_REVIEWING)"), "edited REVIEW posts must return to reviewing status");
        assertTrue(postService.contains("if (!reviewRequired)"), "reviewing posts must not publish public post events");
        assertTrue(postService.contains("events.publish(PostUpdatedEvent.builder()"), "post updates must publish so search can delete stale reviewed/private docs");

        assertTrue(commentCmd.contains("private Boolean reviewRequired"), "comment command must carry reviewRequired");
        assertTrue(interactionController.contains("contentModerationService.checkContent"), "comment controller must use structured moderation decisions");
        assertTrue(interactionController.contains("reviewRequired(moderationDecision.reviewRequired())"), "comment controller must pass REVIEW decision into interaction service");
        assertTrue(interactionController.contains("\"reviewRequired\", moderationDecision.reviewRequired()"), "comment API must tell the frontend when content entered review");

        assertTrue(interactionFacade.contains("COMMENT_STATUS_REVIEWING = 2"), "interaction service must name the reviewing comment status");
        assertTrue(interactionFacade.contains("reviewRequired ? COMMENT_STATUS_REVIEWING : COMMENT_STATUS_NORMAL"), "REVIEW comments must be saved as reviewing");
        assertTrue(interactionFacade.contains("if (!reviewRequired)"), "reviewing comments must not increment counters or publish comment events");
        assertTrue(interactionFacade.contains("CommentCreatedEvent.builder()"), "normal comments must keep the existing notification/event flow");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
