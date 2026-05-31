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

        assertTrue(api.contains("PostDTO getPost(Long postId, Long viewerUid)"), "post detail API must accept viewer uid");
        assertTrue(facade.contains("return getPost(postId, UserContext.get())"), "default getPost must use request user context");
        assertTrue(facade.contains("CacheKeyBuilder.postDetailRaw(postId)"), "detail cache must store raw DTO before visibility filtering");
        assertFalse(facade.contains("post.isVisibleTo(null, false) ? toFullDto(post) : null"), "cache loader must not cache anonymous visibility decisions");
        assertTrue(facade.contains("Objects.equals(dto.getAuthorId(), viewerUid)"), "author must be able to view own restricted posts");
        assertTrue(facade.contains("userFacade.isFollowing(viewerUid, dto.getAuthorId())"), "follower visibility must check follow relation");
        assertTrue(facade.contains("multiLevelCache.evict(CacheKeyBuilder.postDetailRaw(postId))"), "post updates/deletes must evict raw detail cache");
        assertTrue(reportService.contains("postDetailRaw(postId)"), "moderation takedown must evict raw detail cache");
        assertTrue(keys.contains("postDetailRaw(Long postId)"), "raw detail cache key must be centrally defined");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
