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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    public PageResult<FeedItemVO> getFollowingFeed(Long uid, String cursor, int size, Integer domain) {
        double maxScore = parseCursorScore(cursor);
        Set<ZSetOperations.TypedTuple<String>> tuples = feedRedis.readInboxWithScore(uid, maxScore, size);
        return filterByDomain(assembleFromTuples(tuples, size, uid), domain);
    }

    @Override
    public PageResult<FeedItemVO> getRecommendFeed(Long uid, String cursor, int size, Integer domain) {
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
        return filterByDomain(assembleRecommendPosts(ranked, uid, page.getNextCursor(), Boolean.TRUE.equals(page.getHasMore()), intent), domain);
    }

    @Override
    public PageResult<FeedItemVO> getLatestFeed(Long viewerUid, String cursor, int size, Integer domain) {
        PageResult<FeedItemVO> dbPage = fallbackLatestFromDb(viewerUid, cursor, size);
        if (cursor == null || cursor.isBlank()) {
            return filterByDomain(mergeFirstPageLatestWithRedis(viewerUid, size, dbPage), domain);
        }
        if (dbPage != null && dbPage.getItems() != null && !dbPage.getItems().isEmpty()) {
            return filterByDomain(dbPage, domain);
        }
        Set<ZSetOperations.TypedTuple<String>> tuples = readGlobalLatestSafely(parseCursorScore(cursor), size);
        if (tuples == null || tuples.isEmpty()) {
            return PageResult.empty();
        }
        return filterByDomain(assembleFromTuples(tuples, size, viewerUid), domain);
    }

    private PageResult<FeedItemVO> mergeFirstPageLatestWithRedis(Long viewerUid,
                                                                 int size,
                                                                 PageResult<FeedItemVO> dbPage) {
        Set<ZSetOperations.TypedTuple<String>> tuples = readGlobalLatestSafely(Double.MAX_VALUE, size);
        boolean hasDbItems = dbPage != null && dbPage.getItems() != null && !dbPage.getItems().isEmpty();
        boolean hasRedisItems = tuples != null && !tuples.isEmpty();
        if (!hasDbItems && !hasRedisItems) {
            return PageResult.empty();
        }
        if (!hasRedisItems) {
            return dbPage;
        }
        Map<Long, Long> redisScores = tuples == null ? Map.of() : tuples.stream()
                .map(this::toPostIdAndScore)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(pair -> pair[0], pair -> pair[1], (left, right) -> Math.max(left, right)));
        PageResult<FeedItemVO> redisPage = assembleFromTuples(tuples, size, viewerUid);
        Map<Long, FeedItemVO> deduped = new LinkedHashMap<>();
        addFeedItems(deduped, redisPage == null ? null : redisPage.getItems());
        addFeedItems(deduped, dbPage == null ? null : dbPage.getItems());
        if (deduped.isEmpty()) {
            return PageResult.empty();
        }
        List<FeedItemVO> merged = deduped.values().stream()
                .sorted(Comparator.<FeedItemVO>comparingLong(item -> latestScore(item, redisScores)).reversed()
                        .thenComparing(item -> item.getPost() == null ? null : item.getPost().getId(),
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        boolean candidateHasMore = merged.size() > size;
        List<FeedItemVO> items = candidateHasMore ? merged.subList(0, size) : merged;
        boolean hasMore = candidateHasMore
                || Boolean.TRUE.equals(dbPage == null ? null : dbPage.getHasMore())
                || Boolean.TRUE.equals(redisPage == null ? null : redisPage.getHasMore());
        String nextCursor = dbPage == null ? null : dbPage.getNextCursor();
        if (nextCursor == null && redisPage != null) {
            nextCursor = redisPage.getNextCursor();
        }
        return PageResult.of(items, nextCursor, hasMore);
    }

    private Set<ZSetOperations.TypedTuple<String>> readGlobalLatestSafely(double maxScore, int size) {
        try {
            return feedRedis.readGlobalLatest(maxScore, size);
        } catch (Exception e) {
            log.warn("feed redis global latest read failed, fallback to db page: {}", e.toString());
            return Set.of();
        }
    }

    private void addFeedItems(Map<Long, FeedItemVO> target, List<FeedItemVO> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        for (FeedItemVO item : source) {
            if (item == null || item.getPost() == null || item.getPost().getId() == null) {
                continue;
            }
            target.putIfAbsent(item.getPost().getId(), item);
        }
    }

    private long latestScore(FeedItemVO item, Map<Long, Long> redisScores) {
        if (item == null || item.getPost() == null) {
            return 0L;
        }
        Long redisScore = redisScores.get(item.getPost().getId());
        if (redisScore != null) {
            return redisScore;
        }
        LocalDateTime createTime = item.getPost().getCreateTime();
        return createTime == null ? 0L : createTime.toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
    }

    @Override
    public PageResult<FeedItemVO> getHotFeed(Long viewerUid, String cursor, int size, Integer domain) {
        var page = postFacade.getHot(cursor, size);
        if (page == null || page.getItems() == null || page.getItems().isEmpty()) {
            return filterByDomain(getLatestFeed(viewerUid, cursor, size, domain), domain);
        }
        return filterByDomain(assembleFromPosts(page.getItems(), viewerUid, page.getNextCursor(), Boolean.TRUE.equals(page.getHasMore())), domain);
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
                .map(this::toPostIdAndScore)
                .filter(Objects::nonNull)
                .toList();
        if (idsAndScores.isEmpty()) {
            return PageResult.empty();
        }

        List<Long> postIds = idsAndScores.stream().map(a -> a[0]).toList();
        var posts = postFacade.batchGetPosts(postIds, viewerUid);
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
        boolean hasMore = idsAndScores.size() == size;
        String next = null;
        if (hasMore) {
            long lastScore = idsAndScores.get(idsAndScores.size() - 1)[1];
            next = String.valueOf(lastScore);
        }
        return PageResult.of(items, next, hasMore);
    }

    private long[] toPostIdAndScore(ZSetOperations.TypedTuple<String> tuple) {
        if (tuple == null || tuple.getValue() == null) {
            return null;
        }
        try {
            return new long[]{Long.parseLong(tuple.getValue()), tuple.getScore() == null ? 0L : tuple.getScore().longValue()};
        } catch (NumberFormatException e) {
            log.warn("feed redis tuple skipped: invalid postId={}", tuple.getValue());
            return null;
        }
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

    private PageResult<FeedItemVO> assembleRecommendPosts(List<PostBriefDTO> posts,
                                                          Long viewerUid,
                                                          String nextCursor,
                                                          boolean hasMore,
                                                          UserIntentDTO intent) {
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
                .recommendationReasons(recommendationReasons(p, counters.get(p.getId()), intent))
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
        String engineeringArea = firstNonBlank(firstArrayValue(ext, "techStacks"), ext.path("company").asText(""));
        String technicalScenario = firstNonBlank(ext.path("scenario").asText(""), ext.path("position").asText(""));
        String content = searchableText(post, ext);
        double score = 0D;
        if (matchesAny(engineeringArea, intent.getTargetCompanies())) {
            score += 28D;
        }
        if (matchesAny(technicalScenario, intent.getTargetPositions())) {
            score += 22D;
        }
        if (matchesAny(content, intent.getTechStack())) {
            score += 14D;
        }
        if (!firstMatchedInterest(content, interestCandidates(intent)).isBlank()) {
            score += 36D;
        }
        return score;
    }

    private List<String> recommendationReasons(PostBriefDTO post, PostCounterDTO counter, UserIntentDTO intent) {
        List<String> reasons = new ArrayList<>();
        JsonNode ext = parseExt(post.getExtJson());
        String engineeringArea = firstNonBlank(firstArrayValue(ext, "techStacks"), ext.path("company").asText(""));
        String technicalScenario = firstNonBlank(ext.path("scenario").asText(""), ext.path("position").asText(""));
        String content = searchableText(post, ext);
        String matchedInterest = intent == null ? "" : firstMatchedInterest(content, interestCandidates(intent));
        if (!matchedInterest.isBlank()) {
            reasons.add("匹配你的兴趣：" + matchedInterest);
        }
        if (intent != null && matchesAny(engineeringArea, intent.getTargetCompanies())) {
            reasons.add("覆盖你关注的工程场景：" + engineeringArea);
        }
        if (intent != null && matchesAny(technicalScenario, intent.getTargetPositions())) {
            reasons.add("关联你关注的技术主题：" + technicalScenario);
        }
        if (intent != null && matchesAny(content, intent.getTechStack())) {
            reasons.add("包含你关注的技术栈");
        }
        long heat = counter == null ? 0L : safe(counter.getLikeCount()) + safe(counter.getFavoriteCount()) + safe(counter.getCommentCount());
        if (heat >= 3) {
            reasons.add("近期社区互动热度较高");
        }
        if (post.getCreateTime() != null && Duration.between(post.getCreateTime(), LocalDateTime.now()).toHours() <= 24) {
            reasons.add("24 小时内新发布");
        }
        if ((post.getTags() == null ? 0 : post.getTags().size()) >= 2) {
            reasons.add("技术标签完整，便于快速判断主题");
        }
        if (reasons.isEmpty()) {
            reasons.add("根据近期内容质量和活跃度推荐");
        }
        return reasons.stream().limit(3).toList();
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

    private static String searchableText(PostBriefDTO post, JsonNode ext) {
        if (post == null) {
            return "";
        }
        String tags = post.getTags() == null ? "" : post.getTags().stream()
                .filter(Objects::nonNull)
                .flatMap(tag -> Stream.of(tag.getName(), tag.getSlug(), tag.getCategory()))
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
        String extText = Stream.of(
                        ext.path("contentType").asText(""),
                        ext.path("topic").asText(""),
                        ext.path("category").asText(""),
                        ext.path("scenario").asText(""),
                        ext.path("position").asText(""),
                        ext.path("company").asText(""),
                        firstArrayValue(ext, "techStacks"),
                        firstArrayValue(ext, "topics"),
                        firstArrayValue(ext, "tags"))
                .collect(Collectors.joining(" "));
        return clean(String.join(" ",
                post.getTitle() == null ? "" : post.getTitle(),
                post.getSummary() == null ? "" : post.getSummary(),
                tags,
                extText));
    }

    private static List<String> interestCandidates(UserIntentDTO intent) {
        if (intent == null) {
            return List.of();
        }
        return Stream.of(
                        intent.getInterestTopics(),
                        intent.getInterestTags(),
                        intent.getContentPreferences())
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static String firstMatchedInterest(String source, List<String> candidates) {
        String text = clean(source).toLowerCase();
        if (text.isBlank() || candidates == null || candidates.isEmpty()) {
            return "";
        }
        return candidates.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .filter(item -> text.contains(item.toLowerCase()))
                .findFirst()
                .orElse("");
    }

    private static String firstArrayValue(JsonNode ext, String field) {
        JsonNode node = ext.path(field);
        if (!node.isArray()) {
            return "";
        }
        for (JsonNode item : node) {
            String value = clean(item.asText(""));
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String firstNonBlank(String first, String fallback) {
        String normalized = clean(first);
        if (!normalized.isBlank()) {
            return normalized;
        }
        return clean(fallback);
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

    /**
     * Filter PageResult items by domain. Items without a post or without a domain are excluded.
     */
    private PageResult<FeedItemVO> filterByDomain(PageResult<FeedItemVO> page, Integer domain) {
        if (domain == null || page == null || page.getItems() == null || page.getItems().isEmpty()) {
            return page;
        }
        List<FeedItemVO> filtered = page.getItems().stream()
                .filter(item -> item.getPost() != null
                        && item.getPost().getDomain() != null
                        && item.getPost().getDomain().equals(domain))
                .toList();
        boolean hasMore = page.getHasMore() != null && page.getHasMore();
        return PageResult.of(filtered, page.getNextCursor(), hasMore);
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
