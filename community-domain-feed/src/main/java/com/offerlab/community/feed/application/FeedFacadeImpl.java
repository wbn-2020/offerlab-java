package com.offerlab.community.feed.application;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.feed.api.FeedFacade;
import com.offerlab.community.feed.api.dto.FeedItemVO;
import com.offerlab.community.feed.infrastructure.FeedInboxRedis;
import com.offerlab.community.interaction.api.InteractionFacade;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.PostCounterDTO;
import com.offerlab.community.user.api.UserFacade;
import com.offerlab.community.user.api.dto.UserBriefDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedFacadeImpl implements FeedFacade {

    private final FeedInboxRedis feedRedis;
    private final PostFacade postFacade;
    private final UserFacade userFacade;
    private final InteractionFacade interactionFacade;

    @Override
    public PageResult<FeedItemVO> getFollowingFeed(Long uid, String cursor, int size) {
        double maxScore = parseCursorScore(cursor);
        Set<ZSetOperations.TypedTuple<String>> tuples = feedRedis.readInboxWithScore(uid, maxScore, size);
        return assembleFromTuples(tuples, size, uid);
    }

    @Override
    public PageResult<FeedItemVO> getRecommendFeed(Long uid, String cursor, int size) {
        // MVP：先复用最新流；二期接入用户求职意向 + 热度
        return getLatestFeed(uid, cursor, size);
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
        // MVP：先复用最新流；二期接入热度算法
        return getLatestFeed(viewerUid, cursor, size);
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
        List<Long> postIds = page.getItems().stream().map(PostBriefDTO::getId).toList();
        var counters = postFacade.batchGetCounters(postIds);
        var authors = userFacade.batchGetUserBriefs(page.getItems().stream()
                .map(PostBriefDTO::getAuthorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        List<FeedItemVO> items = page.getItems().stream().map(p -> FeedItemVO.builder()
                .post(p)
                .author(authors.get(p.getAuthorId()))
                .counter(counters.get(p.getId()))
                .myInteraction(viewerUid == null ? null : FeedItemVO.MyInteraction.builder()
                        .liked(interactionFacade.hasLiked(viewerUid, p.getId()))
                        .favorited(interactionFacade.hasFavorited(viewerUid, p.getId()))
                        .build())
                .build()).toList();
        return PageResult.of(items, page.getNextCursor(), Boolean.TRUE.equals(page.getHasMore()));
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
