package com.offerlab.community.search.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PostSearchConsistencyGuardTest {

    @Test
    void searchIndexMustDeleteStaleDocumentsAndFilterVisiblePosts() throws Exception {
        String indexer = read("src/main/java/com/offerlab/community/search/application/PostSearchIndexer.java");
        String listener = read("src/main/java/com/offerlab/community/search/application/PostSearchEventListener.java");
        String retryService = read("src/main/java/com/offerlab/community/search/application/SearchIndexRetryService.java");
        String retryMapper = read("src/main/java/com/offerlab/community/search/infrastructure/persistence/mapper/SearchIndexRetryTaskMapper.java");
        String retryPo = read("src/main/java/com/offerlab/community/search/infrastructure/persistence/po/SearchIndexRetryTaskPO.java");
        String opsController = read("src/main/java/com/offerlab/community/search/controller/OpsController.java");
        String facade = read("src/main/java/com/offerlab/community/search/application/SearchFacadeImpl.java");
        String taskService = read("src/main/java/com/offerlab/community/search/application/SearchIndexTaskService.java");
        String postService = read("../community-domain-post/src/main/java/com/offerlab/community/post/application/PostApplicationService.java");
        String resolver = read("../community-infrastructure/src/main/java/com/offerlab/community/infra/mq/producer/EventTopicResolver.java");
        String initSql = read("../db/init/02_post.sql");
        String migration = read("../db/migration/20260530_search_index_retry_task.sql");

        assertTrue(indexer.contains("deletePostDocument(postId)"), "indexer must delete ES docs when a post becomes non-indexable");
        assertTrue(indexer.contains("elasticsearch.deleteDocument"), "indexer must call ES deleteDocument for stale posts");
        assertTrue(indexer.contains("boolean ensured = enabled && available && ensurePostIndex()"), "search status must actively recover stale indexReady state");
        assertTrue(indexer.contains("status.put(\"diagnosticMessage\""), "search status must keep ops diagnostics separate from user-facing copy");
        assertTrue(indexer.contains("List<Long> postIds = posts.stream()"), "post index rebuild must collect each page of post ids");
        assertTrue(indexer.contains("extensionMapper.selectBatchIds(postIds)"), "post index rebuild must batch-load extensions instead of querying per document");
        assertTrue(indexer.contains("counterMapper.selectBatchIds(postIds)"), "post index rebuild must batch-load counters instead of querying per document");
        assertTrue(indexer.contains("selectTagsByPostIds(postIds)"), "post index rebuild must batch-load tags instead of querying per document");
        assertTrue(indexer.contains("tags.getOrDefault(post.getId(), List.of())"), "post index rebuild must pass page-local tag groups into document construction");
        assertTrue(indexer.contains("props.put(\"tags\", Map.of(\"type\", \"nested\", \"properties\", Map.of(")
                        && indexer.contains("\"synonyms\", text"),
                "post_idx mapping must keep tags as nested before adding tag synonym fields");
        assertTrue(listener.contains("PostDeletedEvent"), "search listener must consume post delete events");
        assertTrue(listener.contains("indexer.deletePost(event.getPostId())"), "delete event must remove the ES document");
        assertTrue(listener.contains("retryService.enqueueIndex"), "index failures must enqueue durable retry tasks");
        assertTrue(listener.contains("retryService.enqueueDelete"), "delete failures must enqueue durable retry tasks");
        assertTrue(postService.contains("PostDeletedEvent.builder()"), "post delete must publish a deletion event");
        assertTrue(postService.contains("events.publish(PostUpdatedEvent.builder()"), "post update must publish even when visibility/status changes");
        assertTrue(resolver.contains("PostDeletedEvent"), "outbox topic resolver must route post deleted events");
        for (String trustedEvent : new String[] {
                "PostUsefulFeedbackChangedEvent",
                "ContentSuggestionSubmittedEvent",
                "ContentSuggestionDecidedEvent",
                "QuestionStateChangedEvent",
                "PostFreshnessChangedEvent",
                "AnswerAcceptedEvent"
        }) {
            assertTrue(resolver.contains(trustedEvent),
                    "outbox topic resolver must route trusted-content event " + trustedEvent);
        }
        assertTrue(resolver.contains("readLong(event, \"getPostId\")"),
                "trusted-content outbox records must aggregate by post instead of aggregateId=0");

        assertTrue(facade.contains("filterVisibleSearchResults"), "ES results must pass through a visibility filter");
        assertTrue(facade.contains("postFacade.batchGetPosts"), "search visibility fallback must use PostFacade current-state reads");
        assertTrue(facade.contains("stale elasticsearch post filtered"), "filtered stale ES hits must be observable in logs");
        assertTrue(facade.contains("visibleSuggestionSources"), "ES suggestions must also pass through current visibility checks");
        assertTrue(facade.contains("Map.of(\"term\", Map.of(\"visibility\", 1))"), "ES suggestions must filter public visibility at query time");
        assertTrue(facade.contains("\"postId\""), "ES suggestions must fetch numeric post ids for current-state visibility checks");
        assertTrue(facade.contains("stale elasticsearch suggestion filtered"), "filtered stale ES suggestion hits must be observable in logs");
        assertTrue(facade.contains("int scanLimit = elasticsearchScanLimit(limit)"), "ES search must over-fetch before applying visibility filters");
        assertTrue(facade.contains("body.put(\"size\", scanLimit)"), "ES search request size must use the over-fetch limit");
        assertTrue(facade.contains("boolean hasMore = visibleItems.size() > limit"), "ES hasMore must be calculated after visibility filtering");
        assertTrue(facade.contains("visibleItems.subList(0, limit)"), "ES search must trim over-fetched visible results before returning the page");
        assertTrue(facade.contains("isSparseAfterVisibilityFiltering"), "sparse ES pages must be detected after visibility filtering");
        assertTrue(facade.contains("rawHitCount() >= esPage.scanLimit()"), "sparse detection must only trigger when ES exhausted the scan window");
        assertTrue(facade.contains("shouldUseMysqlFallback"), "sparse ES pages must be eligible for MySQL compensation");
        assertTrue(taskService.contains("REDIS_ACTIVE_REBUILD_KEY"), "post index rebuild task must use a distributed active gate");
        assertTrue(taskService.contains("setIfAbsent"), "post index rebuild distributed gate must be claimed atomically");
        assertTrue(taskService.contains("remoteActiveSnapshot"), "post index rebuild must return the active remote task instead of creating a duplicate");
        assertTrue(taskService.contains("releaseDistributedActiveTask"), "post index rebuild must release its distributed gate after completion");
        assertTrue(taskService.contains("ThreadPoolExecutor"), "post index rebuild must use a dedicated bounded executor");
        assertTrue(taskService.contains("ArrayBlockingQueue<>(1)"), "post index rebuild executor must have a bounded queue");
        assertTrue(!taskService.contains("ForkJoinPool.commonPool()"), "post index rebuild must not use the JVM common pool for blocking rebuild work");

        assertTrue(retryService.contains("@Scheduled(fixedDelay = 5000)"), "search retry service must periodically replay due tasks");
        assertTrue(retryService.contains("claimDue(owner, lockUntil, BATCH_SIZE)"), "search retry service must claim tasks before replaying");
        assertTrue(retryService.contains("indexer.indexPost(task.getPostId())"), "index retry must re-run the indexer");
        assertTrue(retryService.contains("indexer.deletePost(task.getPostId())"), "delete retry must re-run the delete path");
        assertTrue(retryService.contains("MAX_RETRY = 5"), "search retry service must cap retries");
        assertTrue(retryService.contains("Math.pow(2, retryCount) * 30"), "search retry service must back off between attempts");
        assertTrue(retryService.contains("replayFailed"), "failed search retry tasks must be manually replayable");
        assertTrue(retryService.contains("countDuePending"), "search retry service must expose observable queue status");
        assertTrue(retryMapper.contains("ON DUPLICATE KEY UPDATE"), "retry enqueue must be idempotent by post key");
        assertTrue(retryMapper.contains("UPDATE t_search_index_retry_task"), "retry mapper must support state transitions");
        assertTrue(retryMapper.contains("markFailedForRetry"), "failed search retry tasks must be manually replayable");
        assertTrue(retryPo.contains("@TableName(\"t_search_index_retry_task\")"), "retry task PO must map to the durable task table");
        assertTrue(opsController.contains("/search-index-retry-tasks/{id}/replay"), "ops API must allow single failed task replay");
        assertTrue(opsController.contains("/search-index-retry-tasks/replay-batch"), "ops API must allow batch failed task replay");
        assertTrue(opsController.contains("SEARCH_INDEX_RETRY_REPLAY"), "manual search retry replay must leave an audit trail");
        assertTrue(initSql.contains("CREATE TABLE t_search_index_retry_task"), "fresh DB init must create search retry task table");
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS t_search_index_retry_task"), "migration must add retry task table non-destructively");
        assertTrue(migration.contains("UNIQUE KEY uk_search_index_retry_dedup"), "retry table must be idempotent by post key");
        assertTrue(migration.contains("idx_search_index_retry_due"), "retry table must index due pending tasks");
    }

    @Test
    void mysqlFallbackMustPassThroughPostFacadeForAnonymousMasking() throws Exception {
        String facade = read("src/main/java/com/offerlab/community/search/application/SearchFacadeImpl.java");
        String searchByMysql = methodBody(facade, "private PageResult<PostBriefDTO> searchByMysql");

        assertTrue(searchByMysql.contains("filterVisibleSearchResults(items, includeTestData)"),
                "MySQL fallback search must reuse PostFacade visibility/anonymous masking.");
        assertTrue(!searchByMysql.contains("enrich(items)"),
                "MySQL fallback search must not enrich authors directly from raw authorId.");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signaturePrefix) {
        int start = source.indexOf(signaturePrefix);
        if (start < 0) {
            return "";
        }
        int next = source.indexOf("\n    private ", start + signaturePrefix.length());
        return next < 0 ? source.substring(start) : source.substring(start, next);
    }
}
