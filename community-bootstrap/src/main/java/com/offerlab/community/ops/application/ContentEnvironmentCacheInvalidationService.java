package com.offerlab.community.ops.application;

import com.offerlab.community.feed.infrastructure.FeedInboxRedis;
import com.offerlab.community.infra.redis.cache.CacheKeyBuilder;
import com.offerlab.community.infra.redis.cache.RequiredCacheEvictor;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.question.application.QuestionFacade;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ContentEnvironmentCacheInvalidationService {

    private static final int MAX_POST_IDS = 100;

    private final PostFacade postFacade;
    private final QuestionFacade questionFacade;
    private final FeedInboxRedis feedInboxRedis;
    private final RequiredCacheEvictor requiredCacheEvictor;

    public ContentEnvironmentCacheInvalidationService(PostFacade postFacade,
                                                       QuestionFacade questionFacade,
                                                       FeedInboxRedis feedInboxRedis,
                                                       RequiredCacheEvictor requiredCacheEvictor) {
        this.postFacade = postFacade;
        this.questionFacade = questionFacade;
        this.feedInboxRedis = feedInboxRedis;
        this.requiredCacheEvictor = requiredCacheEvictor;
    }

    public InvalidationResult invalidate(String operationId, Collection<Long> requestedPostIds) {
        List<Long> postIds = normalizePostIds(requestedPostIds);
        Map<Long, PostDTO> metadata = loadMetadata(postIds);
        Map<Long, List<Long>> nonCommunityPostsByAuthor = nonCommunityPostsByAuthor(metadata);
        List<String> exactCacheKeys = exactCacheKeys(postIds,
                questionFacade.resolveCompanyPrepCacheKeysForPosts(postIds));

        int evictedKeyCount = requiredCacheEvictor.evictExact(exactCacheKeys);
        FeedInboxRedis.RemovalResult feedSummary =
                feedInboxRedis.removePostsRequired(nonCommunityPostsByAuthor);

        return new InvalidationResult(
                operationId,
                postIds,
                evictedKeyCount,
                exactCacheKeys.size() - postIds.size() * 3,
                feedSummary.globalLatestRemoved(),
                feedSummary.authorTimelineRemoved(),
                feedSummary.followerInboxEntriesRemoved(),
                feedSummary.followerInboxesChecked(),
                List.of("recommendation", "hot-candidate", "topic", "tag",
                        "user-contribution", "analytics", "seo"),
                "LIVE_DATABASE_FILTERED"
        );
    }

    private Map<Long, PostDTO> loadMetadata(List<Long> postIds) {
        Map<Long, PostDTO> metadata = new LinkedHashMap<>();
        for (Long postId : postIds) {
            PostDTO post = postFacade.getPostMetadata(postId);
            if (post == null || post.getAuthorId() == null || post.getAuthorId() <= 0) {
                throw new IllegalArgumentException("post metadata unavailable for exact id " + postId);
            }
            metadata.put(postId, post);
        }
        return metadata;
    }

    private Map<Long, List<Long>> nonCommunityPostsByAuthor(Map<Long, PostDTO> metadata) {
        Map<Long, List<Long>> byAuthor = new LinkedHashMap<>();
        metadata.forEach((postId, post) -> {
            if (!Post.isCommunityContent(post.getContentEnvironment())) {
                byAuthor.computeIfAbsent(post.getAuthorId(), ignored -> new ArrayList<>()).add(postId);
            }
        });
        return byAuthor;
    }

    private List<String> exactCacheKeys(List<Long> postIds, Collection<String> questionCacheKeys) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (Long postId : postIds) {
            keys.add(CacheKeyBuilder.postDetail(postId));
            keys.add(CacheKeyBuilder.postDetailRaw(postId));
            keys.add(CacheKeyBuilder.postCounter(postId));
        }
        if (questionCacheKeys != null) {
            questionCacheKeys.stream()
                    .filter(Objects::nonNull)
                    .filter(key -> !key.isBlank())
                    .map(String::trim)
                    .forEach(keys::add);
        }
        return List.copyOf(keys);
    }

    private static List<Long> normalizePostIds(Collection<Long> requestedPostIds) {
        if (requestedPostIds == null || requestedPostIds.isEmpty()) {
            throw new IllegalArgumentException("postIds must not be empty");
        }
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        for (Long postId : requestedPostIds) {
            if (postId == null || postId <= 0) {
                throw new IllegalArgumentException("postIds must contain positive ids");
            }
            if (!normalized.add(postId)) {
                throw new IllegalArgumentException("postIds must not contain duplicates");
            }
            if (normalized.size() > MAX_POST_IDS) {
                throw new IllegalArgumentException("postIds exceeds the controlled limit");
            }
        }
        return List.copyOf(normalized);
    }

    public record InvalidationResult(String operationId,
                                     List<Long> postIds,
                                     int exactCacheKeysEvicted,
                                     int questionCompanyCacheKeysEvicted,
                                     long globalLatestEntriesRemoved,
                                     long authorTimelineEntriesRemoved,
                                     long followerInboxEntriesRemoved,
                                     int followerInboxesChecked,
                                     List<String> liveQueryDerivedSurfaces,
                                     String liveQueryDerivedMode) {
    }

}
