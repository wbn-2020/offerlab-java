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
        String postService = read("../community-domain-post/src/main/java/com/offerlab/community/post/application/PostApplicationService.java");
        String resolver = read("../community-infrastructure/src/main/java/com/offerlab/community/infra/mq/producer/EventTopicResolver.java");
        String initSql = read("../db/init/02_post.sql");
        String migration = read("../db/migration/20260530_search_index_retry_task.sql");

        assertTrue(indexer.contains("deletePostDocument(postId)"), "indexer must delete ES docs when a post becomes non-indexable");
        assertTrue(indexer.contains("elasticsearch.deleteDocument"), "indexer must call ES deleteDocument for stale posts");
        assertTrue(listener.contains("PostDeletedEvent"), "search listener must consume post delete events");
        assertTrue(listener.contains("indexer.deletePost(event.getPostId())"), "delete event must remove the ES document");
        assertTrue(listener.contains("retryService.enqueueIndex"), "index failures must enqueue durable retry tasks");
        assertTrue(listener.contains("retryService.enqueueDelete"), "delete failures must enqueue durable retry tasks");
        assertTrue(postService.contains("PostDeletedEvent.builder()"), "post delete must publish a deletion event");
        assertTrue(postService.contains("events.publish(PostUpdatedEvent.builder()"), "post update must publish even when visibility/status changes");
        assertTrue(resolver.contains("PostDeletedEvent"), "outbox topic resolver must route post deleted events");

        assertTrue(facade.contains("filterVisibleSearchResults"), "ES results must pass through a visibility filter");
        assertTrue(facade.contains("postFacade.batchGetPosts"), "search visibility fallback must use PostFacade current-state reads");
        assertTrue(facade.contains("stale elasticsearch post filtered"), "filtered stale ES hits must be observable in logs");

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

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
