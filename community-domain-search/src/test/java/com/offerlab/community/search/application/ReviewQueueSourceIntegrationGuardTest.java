package com.offerlab.community.search.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewQueueSourceIntegrationGuardTest {

    @Test
    void legacyReviewSourcesMustPublishAndResolveUnifiedQueueItems() throws Exception {
        String command = read("../community-infrastructure/src/main/java/com/offerlab/community/infra/review/ReviewQueueItemCommand.java");
        String event = read("../community-infrastructure/src/main/java/com/offerlab/community/infra/review/ReviewQueueUpsertRequestedEvent.java");
        String publisher = read("../community-infrastructure/src/main/java/com/offerlab/community/infra/review/ReviewQueuePublisher.java");
        String noop = read("../community-infrastructure/src/main/java/com/offerlab/community/infra/review/NoopReviewQueuePublisher.java");
        String service = read("src/main/java/com/offerlab/community/search/application/ReviewQueueService.java");
        String eventListener = read("src/main/java/com/offerlab/community/search/application/ReviewQueueUpsertRequestedEventListener.java");
        String mapper = read("src/main/java/com/offerlab/community/search/infrastructure/persistence/mapper/ReviewQueueMapper.java");
        String postReport = read("../community-domain-post/src/main/java/com/offerlab/community/post/application/PostReportService.java");
        String commentReport = read("../community-domain-interaction/src/main/java/com/offerlab/community/interaction/application/CommentReportService.java");
        String moderation = read("../community-infrastructure/src/main/java/com/offerlab/community/infra/moderation/ContentModerationService.java");
        String questionFacade = read("../community-domain-question/src/main/java/com/offerlab/community/question/application/QuestionFacadeImpl.java");

        assertTrue(command.contains("record ReviewQueueItemCommand"), "legacy sources must share a typed review queue command");
        assertTrue(event.contains("record ReviewQueueUpsertRequestedEvent"), "infrastructure moderation must cross the domain boundary through an event");
        assertTrue(publisher.contains("void upsert(ReviewQueueItemCommand command)"), "publisher must expose source upsert");
        assertTrue(publisher.contains("void resolve(String sourceType, Long sourceId"), "publisher must expose source resolve");
        assertTrue(noop.contains("@ConditionalOnMissingBean(ReviewQueuePublisher.class)"), "domain modules must have a no-op fallback outside the search module");

        assertTrue(service.contains("implements ReviewQueuePublisher"), "search review queue service must implement the shared publisher");
        assertTrue(eventListener.contains("reviewQueueService.upsert(event.command())"), "search must consume moderation queue events");
        assertTrue(service.contains("@Primary"), "real queue publisher must win over the no-op fallback when search module is present");
        assertTrue(service.contains("upsertInternal"), "manual and source-created items must share queue creation logic");
        assertTrue(service.contains("resolveSourceRequired"), "operator-triggered source resolution must have a fail-closed path");
        assertTrue(service.contains("operatorUid != null"), "source resolution must distinguish operator-triggered changes from system sync");
        assertTrue(service.contains("auditService.recordRequired(operatorUid, \"REVIEW_QUEUE_SOURCE_RESOLVE\""),
                "operator-triggered source resolution must use required audit");
        assertTrue(service.contains("auditService.record(operatorUid, \"SYSTEM_REVIEW_QUEUE_SOURCE_RESOLVE\""),
                "system source resolution should keep best-effort audit semantics");
        assertTrue(mapper.contains("findBySource"), "queue mapper must find items by source");
        assertTrue(mapper.contains("resolveBySource"), "queue mapper must resolve items by source");

        assertTrue(postReport.contains("\"POST_REPORT\""), "post reports must publish POST_REPORT queue items");
        assertTrue(postReport.contains("publishReportQueueItem(reportId, post, po)"), "post report creation must enqueue a review item");
        assertTrue(postReport.contains("reviewQueuePublisher.resolve(\"POST_REPORT\""), "post report review must resolve the queue item");

        assertTrue(commentReport.contains("\"COMMENT_REPORT\""), "comment reports must publish COMMENT_REPORT queue items");
        assertTrue(commentReport.contains("publishReportQueueItem(reportId, comment, po)"), "comment report creation must enqueue a review item");
        assertTrue(commentReport.contains("reviewQueuePublisher.resolve(\"COMMENT_REPORT\""), "comment report review must resolve the queue item");
        assertTrue(commentReport.contains("adminAuditService.recordRequired(reviewerUid, approved ? \"COMMENT_REPORT_APPROVE\" : \"COMMENT_REPORT_REJECT\""),
                "comment report review must fail closed when required audit is unavailable");

        assertTrue(moderation.contains("\"MODERATION_HIT\""), "REVIEW keyword hits must publish MODERATION_HIT queue items");
        assertTrue(moderation.contains("\"REVIEW\".equals(action)"), "only review hits should enqueue moderation review items");
        assertTrue(moderation.contains("events.publishEvent(new ReviewQueueUpsertRequestedEvent"),
                "moderation review hits must be delivered through the event boundary");
        assertFalse(moderation.contains("private final ReviewQueuePublisher"),
                "moderation must not directly depend on the search queue publisher and recreate a bean cycle");

        assertTrue(questionFacade.contains("\"QUESTION_PENDING\""), "pending questions must publish QUESTION_PENDING queue items");
        assertTrue(questionFacade.contains("publishPendingQuestionQueueItem(po)"), "question extraction must enqueue pending questions");
        assertTrue(questionFacade.contains("reviewQueuePublisher.resolve(\"QUESTION_PENDING\""), "question review must resolve pending question queue items");

        assertTrue(questionFacade.contains("\"AI_TASK_FAILED\""), "failed AI tasks must publish AI_TASK_FAILED queue items");
        assertTrue(questionFacade.contains("publishFailedAiTaskQueueItem(task)"), "AI task failures must enqueue review items");
        assertTrue(questionFacade.contains("reviewQueuePublisher.resolve(\"AI_TASK_FAILED\""), "AI task retry must close failed task queue items");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
