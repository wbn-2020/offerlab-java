package com.offerlab.community.post.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostVisibilityGuardTest {

    @Test
    void postDetailCacheMustNotBakeAnonymousVisibilityDecision() throws Exception {
        String facade = read("src/main/java/com/offerlab/community/post/application/PostFacadeImpl.java");
        String api = read("src/main/java/com/offerlab/community/post/api/PostFacade.java");
        String keys = read("../community-infrastructure/src/main/java/com/offerlab/community/infra/redis/cache/CacheKeyBuilder.java");
        String reportService = read("src/main/java/com/offerlab/community/post/application/PostReportService.java");
        String featuredService = read("src/main/java/com/offerlab/community/post/application/PostFeaturedService.java");
        String knowledgeReviewService = read("src/main/java/com/offerlab/community/post/application/PostKnowledgeReviewService.java");

        assertTrue(api.contains("PostDTO getPost(Long postId, Long viewerUid)"), "post detail API must accept viewer uid");
        assertTrue(facade.contains("return getPost(postId, UserContext.get())"), "default getPost must use request user context");
        assertTrue(facade.contains("CacheKeyBuilder.postDetailRaw(postId)"), "detail cache must store raw DTO before visibility filtering");
        assertFalse(facade.contains("post.isVisibleTo(null, false) ? toFullDto(post) : null"), "cache loader must not cache anonymous visibility decisions");
        assertTrue(facade.contains("Objects.equals(dto.getAuthorId(), viewerUid)"), "author must be able to view own restricted posts");
        assertTrue(facade.contains("userFacade.isFollowing(viewerUid, dto.getAuthorId())"), "follower visibility must check follow relation");
        assertTrue(facade.contains("multiLevelCache.evict(CacheKeyBuilder.postDetailRaw(postId))"), "post updates/deletes must evict raw detail cache");
        assertTrue(reportService.contains("postDetailRaw(postId)"), "moderation takedown must evict raw detail cache");
        assertTrue(keys.contains("postDetailRaw(Long postId)"), "raw detail cache key must be centrally defined");
        assertPostCacheEvictionRunsAfterCommit(featuredService, "featured update");
        assertPostCacheEvictionRunsAfterCommit(knowledgeReviewService, "knowledge review");
        assertPostCacheEvictionRunsAfterCommit(reportService, "report takedown");
    }

    private static void assertPostCacheEvictionRunsAfterCommit(String source, String operation) {
        assertTrue(source.contains("private final AfterCommitExecutor afterCommit;"),
                operation + " must use the shared after-commit executor");
        assertTrue(source.contains("afterCommit.execute(() -> {"),
                operation + " must defer detail-cache eviction until commit");
        assertTrue(countOccurrences(source, "postDetailCache.evict(") == 2,
                operation + " must only evict the rendered and raw detail keys from the deferred task");
    }

    private static int countOccurrences(String source, String token) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
