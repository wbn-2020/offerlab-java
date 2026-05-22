package com.offerlab.community.feed.application;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.feed.api.FeedFacade;
import com.offerlab.community.feed.api.dto.FeedItemVO;
import com.offerlab.community.feed.infrastructure.FeedFeedbackStore;
import com.offerlab.community.feed.infrastructure.FeedInboxRedis;
import com.offerlab.community.interaction.api.InteractionFacade;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.PostCounterDTO;
import com.offerlab.community.user.api.UserFacade;
import com.offerlab.community.user.api.dto.UserBriefDTO;
import com.offerlab.community.user.api.dto.UserIntentDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedFacadeImpl implements FeedFacade {

    private final FeedInboxRedis feedRedis;
    private final FeedFeedbackStore feedbackStore;
    private final PostFacade postFacade;
    private final UserFacade userFacade;
    private final InteractionFacade interactionFacade;
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<FeedItemVO> getFollowingFeed(Long uid, String cursor, int size) {
        double maxScore = parseCursorScore(cursor);
        Set<ZSetOperations.TypedTuple<String>> tuples = feedRedis.readInboxWithScore(uid, maxScore, size);
        return assembleFromTuples(tuples, size, uid);
    }

    @Override
    public PageResult<FeedItemVO> getRecommendFeed(Long uid, String cursor, int size) {
        long c = parseCursorAsEpoch(cursor);
        int candidateSize = Math.min(Math.max(size * 3, size), 50);
        var page = postFacade.getLatest(c, candidateSize);
        if (page == null || page.getItems() == null || page.getItems().isEmpty()) {
            return PageResult.empty();
        }
        UserIntentDTO intent = uid == null ? null : userFacade.getUserIntent(uid);
        Set<Long> hiddenPostIds = feedbackStore.hiddenPostIds(uid);
        List<PostBriefDTO> ranked = page.getItems().stream()
                .filter(post -> post != null && !hiddenPostIds.contains(post.getId()))
                .sorted(Comparator.<PostBriefDTO>comparingDouble(post -> recommendScore(post, intent)).reversed()
                        .thenComparing(PostBriefDTO::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(size)
                .toList();
        return assembleFromPosts(ranked, uid, page.getNextCursor(), Boolean.TRUE.equals(page.getHasMore()));
    }

    @Override
    public PageResult<FeedItemVO> getLatestFeed(Long viewerUid, String cursor, int size) {
        double maxScore = parseCursorScore(cursor);
        Set<ZSetOperations.TypedTuple<String>> tuples = feedRedis.readGlobalLatest(maxScore, size);
        if (tuples == null || tuples.isEmpty()) {
            // Redis 没数据时降级走 DB
            return fallbackLatestFromDb(viewerUid, cursor, size);
        }
        return assembleFromTuples(tuples, size, viewerUid);
    }

    @Override
    public PageResult<FeedItemVO> getHotFeed(Long viewerUid, String cursor, int size) {
        long c = parseCursorAsEpoch(cursor);
        var page = postFacade.getHot(c, size);
        if (page == null || page.getItems() == null || page.getItems().isEmpty()) {
            return getLatestFeed(viewerUid, cursor, size);
        }
        return assembleFromPosts(page.getItems(), viewerUid, page.getNextCursor(), Boolean.TRUE.equals(page.getHasMore()));
    }

    @Override
    public void recordFeedback(Long uid, Long postId, String action, String reason) {
        feedbackStore.record(uid, postId, action, reason);
    }

    private PageResult<FeedItemVO> assembleFromTuples(Set<ZSetOperations.TypedTuple<String>> tuples,
                                                      int size,
                                                      Long viewerUid) {
        if (tuples == null || tuples.isEmpty()) return PageResult.empty();
        List<long[]> idsAndScores = tuples.stream()
                .map(t -> new long[]{Long.parseLong(t.getValue()), t.getScore() == null ? 0L : t.getScore().longValue()})
                .toList();

        List<Long> postIds = idsAndScores.stream().map(a -> a[0]).toList();
        var posts = postFacade.batchGetPosts(postIds);
        var counters = postFacade.batchGetCounters(postIds);
        Set<Long> authorIds = posts.values().stream().map(PostBriefDTO::getAuthorId).collect(Collectors.toSet());
        var authors = userFacade.batchGetUserBriefs(authorIds);

        List<FeedItemVO> items = new ArrayList<>(postIds.size());
        for (long[] pair : idsAndScores) {
            PostBriefDTO p = posts.get(pair[0]);
            if (p == null) continue;
            UserBriefDTO author = authors.get(p.getAuthorId());
            PostCounterDTO counter = counters.get(p.getId());
            FeedItemVO.MyInteraction my = null;
            if (viewerUid != null) {
                my = FeedItemVO.MyInteraction.builder()
                        .liked(interactionFacade.hasLiked(viewerUid, p.getId()))
                        .favorited(interactionFacade.hasFavorited(viewerUid, p.getId()))
                        .build();
            }
            items.add(FeedItemVO.builder()
                    .post(p)
                    .author(author)
                    .counter(counter)
                    .myInteraction(my)
                    .build());
        }
        boolean hasMore = items.size() == size;
        String next = null;
        if (hasMore) {
            long lastScore = idsAndScores.get(idsAndScores.size() - 1)[1];
            next = String.valueOf(lastScore);
        }
        return PageResult.of(items, next, hasMore);
    }

    private PageResult<FeedItemVO> fallbackLatestFromDb(Long viewerUid, String cursor, int size) {
        long c = parseCursorAsEpoch(cursor);
        var page = postFacade.getLatest(c, size);
        if (page == null || page.getItems() == null || page.getItems().isEmpty()) return PageResult.empty();
        return assembleFromPosts(page.getItems(), viewerUid, page.getNextCursor(), Boolean.TRUE.equals(page.getHasMore()));
    }

    private PageResult<FeedItemVO> assembleFromPosts(List<PostBriefDTO> posts,
                                                     Long viewerUid,
                                                     String nextCursor,
                                                     boolean hasMore) {
        if (posts == null || posts.isEmpty()) return PageResult.empty();
        List<Long> postIds = posts.stream().map(PostBriefDTO::getId).toList();
        var counters = postFacade.batchGetCounters(postIds);
        var authors = userFacade.batchGetUserBriefs(posts.stream()
                .map(PostBriefDTO::getAuthorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        List<FeedItemVO> items = posts.stream().map(p -> FeedItemVO.builder()
                .post(p)
                .author(authors.get(p.getAuthorId()))
                .counter(counters.get(p.getId()))
                .myInteraction(viewerUid == null ? null : FeedItemVO.MyInteraction.builder()
                        .liked(interactionFacade.hasLiked(viewerUid, p.getId()))
                        .favorited(interactionFacade.hasFavorited(viewerUid, p.getId()))
                        .build())
                .build()).toList();
        return PageResult.of(items, nextCursor, hasMore);
    }

    private double recommendScore(PostBriefDTO post, UserIntentDTO intent) {
        PostCounterDTO counter = post.getCounter();
        double heat = 0D;
        if (counter != null) {
            heat += safe(counter.getLikeCount()) * 3D;
            heat += safe(counter.getFavoriteCount()) * 4D;
            heat += safe(counter.getCommentCount()) * 5D;
            heat += safe(counter.getViewCount()) * 0.2D;
        }
        double recency = post.getCreateTime() == null
                ? 0D
                : Math.max(0D, 72D - Duration.between(post.getCreateTime(), LocalDateTime.now()).toHours());
        double tagBonus = post.getTags() == null ? 0D : Math.min(post.getTags().size(), 3) * 1.5D;
        return heat + recency + tagBonus + intentScore(post, intent);
    }

    private double intentScore(PostBriefDTO post, UserIntentDTO intent) {
        if (intent == null) {
            return 0D;
        }
        JsonNode ext = parseExt(post.getExtJson());
        String company = clean(ext.path("company").asText(""));
        String position = clean(ext.path("position").asText(""));
        String content = clean((post.getTitle() == null ? "" : post.getTitle()) + " " + (post.getSummary() == null ? "" : post.getSummary()));
        double score = 0D;
        if (matchesAny(company, intent.getTargetCompanies())) {
            score += 28D;
        }
        if (matchesAny(position, intent.getTargetPositions())) {
            score += 22D;
        }
        if (matchesAny(content, intent.getTechStack())) {
            score += 14D;
        }
        return score;
    }

    private JsonNode parseExt(String extJson) {
        if (extJson == null || extJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(extJson);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private static boolean matchesAny(String source, List<String> candidates) {
        String text = clean(source).toLowerCase();
        if (text.isBlank() || candidates == null || candidates.isEmpty()) {
            return false;
        }
        return candidates.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(item -> item.trim().toLowerCase())
                .anyMatch(item -> text.contains(item) || item.contains(text));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static long safe(Long value) {
        return value == null ? 0L : value;
    }

    /** 当 cursor 表示 score (timestamp ms) 时；空则视为 +∞（从最新开始） */
    private double parseCursorScore(String cursor) {
        if (cursor == null || cursor.isBlank()) return Double.MAX_VALUE;
        try {
            return Double.parseDouble(cursor) - 1; // 严格小于
        } catch (NumberFormatException e) {
            return Double.MAX_VALUE;
        }
    }

    private long parseCursorAsEpoch(String cursor) {
        if (cursor == null || cursor.isBlank()) return 0L;
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
