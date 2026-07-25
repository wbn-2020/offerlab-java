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
        String pendingHandler = read("src/main/java/com/offerlab/community/post/application/PostPendingReviewQueueActionHandler.java");
        String sourceResolver = read("src/main/java/com/offerlab/community/post/application/PostReviewQueueSourceDomainResolver.java");
        String commentCmd = read("../community-domain-interaction/src/main/java/com/offerlab/community/interaction/api/dto/CommentCreateCmd.java");
        String interactionController = read("../community-domain-interaction/src/main/java/com/offerlab/community/interaction/controller/InteractionController.java");
        String interactionFacade = read("../community-domain-interaction/src/main/java/com/offerlab/community/interaction/application/InteractionFacadeImpl.java");

        assertTrue(moderationService.contains("record ModerationDecision"), "moderation service must expose a structured decision");
        assertTrue(moderationService.contains("public ModerationDecision checkContent"), "publish flows must be able to inspect REVIEW decisions");
        assertTrue(moderationService.contains("checkContent(uid, scope, values);"), "legacy requireContentAllowed calls must delegate to the structured checker");
        assertTrue(moderationService.contains("ModerationDecision.review(keyword.getKeyword(), contentSummary)"), "REVIEW hits must return a review-required decision");
        assertTrue(moderationService.contains("throw new BizException(ErrorCode.PARAM_ERROR.getCode()"), "BLOCK hits must still reject immediately");

        assertTrue(postCreateCmd.contains("private Boolean reviewRequired"), "post create command must carry reviewRequired");
        assertTrue(postCreateCmd.contains("private Boolean keywordReviewRequired"), "post create command must distinguish keyword review");
        assertTrue(postUpdateCmd.contains("private Boolean reviewRequired"), "post update command must carry reviewRequired");
        assertTrue(postUpdateCmd.contains("private Boolean keywordReviewRequired"), "post update command must distinguish keyword review");
        assertTrue(postController.contains("contentModerationService.checkContent"), "post controller must use structured moderation decisions");
        assertTrue(postController.contains("keywordReviewRequired(moderationDecision.reviewRequired())"), "post controller must identify keyword-triggered review");
        assertTrue(postController.contains("domainConfigService.reviewRequiredForPublish(domain)"), "post create API must apply the effective domain review policy");
        assertTrue(postController.contains("\"reviewRequired\", reviewRequired"), "post APIs must tell the frontend when content entered review");

        assertTrue(postService.contains("reviewRequired ? Post.STATUS_REVIEWING : Post.STATUS_PUBLISHED"), "new REVIEW posts must be saved as reviewing");
        assertTrue(postService.contains("post.setPostStatus(Post.STATUS_REVIEWING)"), "edited REVIEW posts must return to reviewing status");
        assertTrue(postService.contains("springEvents.publishEvent(new ReviewQueueReopenRequestedEvent"),
                "policy-triggered review must reopen the post queue through the event boundary");
        assertTrue(postService.contains("resolvePendingPostReview"), "post-level review must have a lifecycle exit");
        assertTrue(postService.contains("expectedVersion != null")
                        && postService.contains("帖子内容已更新，请重新预览后审核"),
                "post-level review must reject a queue item created for an older post version");
        assertTrue(postService.contains("evictPostDetailAfterCommit"), "review decisions must evict detail cache after commit");
        assertTrue(postService.contains("events.publish(PostUpdatedEvent.builder()"), "post updates must publish so search can delete stale reviewed/private docs");
        assertTrue(pendingHandler.contains("sourceId == null || \"CLOSED\".equals(normalize(status))"),
                "closing a post review queue item must not change the reviewing post");
        assertTrue(sourceResolver.contains("SOURCE_POST_PENDING_REVIEW"), "post-level review queue items must resolve their domain");

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
