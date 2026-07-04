package com.offerlab.community.feed.application;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.feed.api.FeedFacade;
import com.offerlab.community.feed.api.dto.CrossDomainRecommendationVO;
import com.offerlab.community.feed.api.dto.FeedItemVO;
import com.offerlab.community.feed.infrastructure.FeedFeedbackStore;
import com.offerlab.community.feed.infrastructure.FeedInboxRedis;
import com.offerlab.community.interaction.api.InteractionFacade;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.PublicContentFilter;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.PostCounterDTO;
import com.offerlab.community.post.domain.model.Post;
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

    private static final int MAX_DOMAIN_INBOX_SCAN_ROWS = 1000;
    private static final int MAX_CROSS_DOMAIN_CANDIDATE_FETCH_SIZE = 20;
    private static final long NEW_CREATOR_MAX_PUBLIC_POSTS = 3L;
    private static final double NEW_CREATOR_BOOST_SCORE = 12D;
    private static final String NEW_CREATOR_SUPPORT_REASON = "新作者前 3 篇内容扶持";

    private final FeedInboxRedis feedRedis;
    private final FeedFeedbackStore feedbackStore;
    private final PostFacade postFacade;
    private final UserFacade userFacade;
    private final InteractionFacade interactionFacade;
    private final ObjectMapper objectMapper;
    private final RecommendFeedNewCreatorSupportRecorder recommendFeedNewCreatorSupportRecorder;

    @Override
    public PageResult<FeedItemVO> getFollowingFeed(Long uid, String cursor, int size, Integer domain) {
        double maxScore = parseCursorScore(cursor);
        if (domain != null) {
            return getDomainFollowingFeed(uid, maxScore, size, domain);
        }
        Set<ZSetOperations.TypedTuple<String>> tuples = feedRedis.readInboxWithScore(uid, maxScore, size);
        return assembleFromTuples(tuples, size, uid);
    }

    @Override
    public PageResult<FeedItemVO> getRecommendFeed(Long uid, String cursor, int size, Integer domain) {
        long c = parseCursorAsEpoch(cursor);
        var page = domain == null
                ? postFacade.getLatest(c, size)
                : postFacade.listPosts(null, null, null, null, domain, c, size);
        if (page == null || page.getItems() == null || page.getItems().isEmpty()) {
            return PageResult.empty();
        }
        UserIntentDTO intent = uid == null ? null : userFacade.getUserIntent(uid);
        Set<Long> hiddenPostIds = feedbackStore.hiddenPostIds(uid);
        List<PostBriefDTO> visibleCandidates = filterDistributablePosts(page.getItems()).stream()
                .filter(post -> post != null && !hiddenPostIds.contains(post.getId()))
                .toList();
        Map<Long, Long> publicPostCountByAuthor = postFacade.batchCountPublicPublishedPostsByAuthors(visibleCandidates.stream()
                .map(PostBriefDTO::getAuthorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        List<PostBriefDTO> ranked = visibleCandidates.stream()
                .sorted(Comparator.<PostBriefDTO>comparingDouble(post -> recommendScore(post, intent, publicPostCountByAuthor)).reversed()
                        .thenComparing(PostBriefDTO::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(size)
                .toList();
        PageResult<FeedItemVO> result = assembleRecommendPosts(
                ranked,
                uid,
                page.getNextCursor(),
                Boolean.TRUE.equals(page.getHasMore()),
                intent,
                publicPostCountByAuthor);
        recordRecommendFeedNewCreatorSupportStatsSafely(uid, domain, ranked, publicPostCountByAuthor);
        return result;
    }

    @Override
    public PageResult<CrossDomainRecommendationVO> getCrossDomainRecommendations(Long uid, String cursor, int size) {
        int pageSize = Math.max(1, size);
        UserIntentDTO intent = uid == null ? null : userFacade.getUserIntent(uid);
        int sourceDomain = inferCrossDomainSource(intent);
        Set<Long> hiddenPostIds = uid == null ? Set.of() : feedbackStore.hiddenPostIds(uid);
        List<PostBriefDTO> candidates = new ArrayList<>();
        List<String> failedDomains = new ArrayList<>();
        long c = parseCursorAsEpoch(cursor);
        int fetchSize = crossDomainCandidateFetchSize(pageSize);
        for (Integer targetDomain : crossDomainTargetDomains(sourceDomain)) {
            try {
                PageResult<PostBriefDTO> page = postFacade.listPosts(null, null, null, null, targetDomain, c, fetchSize);
                if (page == null || page.getItems() == null || page.getItems().isEmpty()) {
                    continue;
                }
                filterDistributablePosts(page.getItems()).stream()
                        .filter(post -> post != null && !hiddenPostIds.contains(post.getId()))
                        .forEach(candidates::add);
            } catch (RuntimeException e) {
                failedDomains.add(domainName(targetDomain));
                log.warn("cross-domain recommendation candidate load failed, viewerUid={}, targetDomain={}", uid, targetDomain, e);
            }
        }
        if (!candidates.isEmpty()) {
            Map<Long, Long> publicPostCountByAuthor = postFacade.batchCountPublicPublishedPostsByAuthors(candidates.stream()
                    .map(PostBriefDTO::getAuthorId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet()));
            List<PostBriefDTO> ranked = candidates.stream()
                    .sorted(Comparator.<PostBriefDTO>comparingDouble(post -> crossDomainScore(post, intent, publicPostCountByAuthor, sourceDomain)).reversed()
                            .thenComparing(PostBriefDTO::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(pageSize)
                    .toList();
            String fallbackReason = failedDomains.isEmpty()
                    ? null
                    : "部分跨领域候选拉取失败：" + String.join("、", failedDomains);
            return assembleCrossDomainPage(
                    ranked,
                    uid,
                    sourceDomain,
                    intent,
                    publicPostCountByAuthor,
                    ranked.size() >= pageSize && candidates.size() > ranked.size(),
                    failedDomains.isEmpty() ? "cross-domain" : "cross-domain-partial",
                    !failedDomains.isEmpty(),
                    fallbackReason);
        }
        return fallbackCrossDomainRecommendations(uid, cursor, pageSize, sourceDomain);
    }

    @Override
    public PageResult<FeedItemVO> getLatestFeed(Long viewerUid, String cursor, int size, Integer domain) {
        if (domain != null) {
            long c = parseCursorAsEpoch(cursor);
            var page = postFacade.listPosts(null, null, null, null, domain, c, size);
            if (page == null || page.getItems() == null || page.getItems().isEmpty()) {
                return PageResult.empty();
            }
            return assembleFromPosts(page.getItems(), viewerUid, page.getNextCursor(), Boolean.TRUE.equals(page.getHasMore()));
        }
        PageResult<FeedItemVO> dbPage = fallbackLatestFromDb(viewerUid, cursor, size);
        if (cursor == null || cursor.isBlank()) {
            return mergeFirstPageLatestWithRedis(viewerUid, size, dbPage);
        }
        if (dbPage != null && dbPage.getItems() != null && !dbPage.getItems().isEmpty()) {
            return dbPage;
        }
        Set<ZSetOperations.TypedTuple<String>> tuples = readGlobalLatestSafely(parseCursorScore(cursor), size);
        if (tuples == null || tuples.isEmpty()) {
            return PageResult.empty();
        }
        return assembleFromTuples(tuples, size, viewerUid);
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
            if (!PublicContentFilter.isDistributablePost(item.getPost())) {
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
        if (domain != null) {
            var page = postFacade.getHot(cursor, size, domain);
            if (page == null || page.getItems() == null || page.getItems().isEmpty()) {
                return PageResult.empty();
            }
            return assembleFromPosts(page.getItems(), viewerUid, page.getNextCursor(), Boolean.TRUE.equals(page.getHasMore()));
        }
        var page = postFacade.getHot(cursor, size);
        if (page == null || page.getItems() == null || page.getItems().isEmpty()) {
            return getLatestFeed(viewerUid, cursor, size, null);
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
        List<long[]> idsAndScores = idsAndScores(tuples);
        if (idsAndScores.isEmpty()) {
            return PageResult.empty();
        }

        List<Long> postIds = idsAndScores.stream().map(a -> a[0]).toList();
        var posts = postFacade.batchGetPosts(postIds, viewerUid);
        Set<Long> hiddenPostIds = hiddenPostIdsSafely(viewerUid);
        var counters = postFacade.batchGetCounters(postIds);
        Set<Long> authorIds = posts.values().stream().map(PostBriefDTO::getAuthorId).collect(Collectors.toSet());
        var authors = userFacade.batchGetUserBriefs(authorIds);

        List<FeedItemVO> items = new ArrayList<>(postIds.size());
        for (long[] pair : idsAndScores) {
            PostBriefDTO p = posts.get(pair[0]);
            if (p == null || hiddenPostIds.contains(p.getId()) || !PublicContentFilter.isDistributablePost(p)) continue;
            UserBriefDTO author = feedAuthor(p, authors, viewerUid);
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

    private PageResult<FeedItemVO> getDomainFollowingFeed(Long uid, double maxScore, int size, Integer domain) {
        int pageSize = Math.max(1, size);
        int fetchSize = overFetchSize(pageSize);
        List<FeedItemVO> matches = new ArrayList<>(pageSize + 1);
        List<Long> matchScores = new ArrayList<>(pageSize + 1);
        double scanMaxScore = maxScore;
        Long lastRawScore = null;
        boolean sourceHasMore = false;
        int scannedRows = 0;

        while (matches.size() <= pageSize && scannedRows < MAX_DOMAIN_INBOX_SCAN_ROWS) {
            int remainingScanRows = MAX_DOMAIN_INBOX_SCAN_ROWS - scannedRows;
            int currentFetchSize = Math.min(fetchSize, remainingScanRows);
            Set<ZSetOperations.TypedTuple<String>> tuples = feedRedis.readInboxWithScore(uid, scanMaxScore, currentFetchSize);
            List<long[]> idsAndScores = idsAndScores(tuples);
            if (idsAndScores.isEmpty()) {
                sourceHasMore = false;
                break;
            }

            scannedRows += idsAndScores.size();
            lastRawScore = idsAndScores.get(idsAndScores.size() - 1)[1];
            sourceHasMore = idsAndScores.size() == currentFetchSize;

            PageResult<FeedItemVO> rawPage = assembleFromTuples(tuples, currentFetchSize, uid);
            Map<Long, FeedItemVO> itemByPostId = rawPage.getItems().stream()
                    .filter(item -> item != null && item.getPost() != null && item.getPost().getId() != null)
                    .collect(Collectors.toMap(item -> item.getPost().getId(), item -> item, (left, right) -> left));
            for (long[] pair : idsAndScores) {
                FeedItemVO item = itemByPostId.get(pair[0]);
                if (matchesDomain(item, domain)) {
                    matches.add(item);
                    matchScores.add(pair[1]);
                    if (matches.size() > pageSize) {
                        break;
                    }
                }
            }

            if (!sourceHasMore || matches.size() > pageSize) {
                break;
            }
            double nextScanMaxScore = lastRawScore - 1D;
            if (nextScanMaxScore >= scanMaxScore) {
                break;
            }
            scanMaxScore = nextScanMaxScore;
        }

        if (matches.isEmpty()) {
            if (sourceHasMore && lastRawScore != null) {
                return PageResult.<FeedItemVO>empty()
                        .withDiagnostic("domainInboxScanRows", scannedRows)
                        .withDiagnostic("domainInboxScanLimited", scannedRows >= MAX_DOMAIN_INBOX_SCAN_ROWS);
            }
            return PageResult.<FeedItemVO>empty().withDiagnostic("domainInboxScanRows", scannedRows);
        }

        boolean hasMore = matches.size() > pageSize || sourceHasMore;
        List<FeedItemVO> items = matches.size() > pageSize ? matches.subList(0, pageSize) : matches;
        String nextCursor = hasMore ? String.valueOf(matchScores.get(items.size() - 1)) : null;
        return PageResult.of(items, nextCursor, hasMore)
                .withDiagnostic("domainInboxScanRows", scannedRows);
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

    private List<long[]> idsAndScores(Set<ZSetOperations.TypedTuple<String>> tuples) {
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        return tuples.stream()
                .map(this::toPostIdAndScore)
                .filter(Objects::nonNull)
                .toList();
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
        List<PostBriefDTO> visiblePosts = visiblePostsForViewer(posts, viewerUid);
        if (visiblePosts.isEmpty()) return PageResult.empty();
        List<Long> postIds = visiblePosts.stream().map(PostBriefDTO::getId).toList();
        var counters = postFacade.batchGetCounters(postIds);
        var authors = userFacade.batchGetUserBriefs(visiblePosts.stream()
                .map(PostBriefDTO::getAuthorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        List<FeedItemVO> items = visiblePosts.stream().map(p -> FeedItemVO.builder()
                .post(p)
                .author(feedAuthor(p, authors, viewerUid))
                .counter(counters.get(p.getId()))
                .myInteraction(viewerUid == null ? null : FeedItemVO.MyInteraction.builder()
                        .liked(interactionFacade.hasLiked(viewerUid, p.getId()))
                        .favorited(interactionFacade.hasFavorited(viewerUid, p.getId()))
                        .build())
                .build()).toList();
        return PageResult.of(items, nextCursor, hasMore);
    }

    private UserBriefDTO feedAuthor(PostBriefDTO post, Map<Long, UserBriefDTO> authors, Long viewerUid) {
        if (post == null) {
            return null;
        }
        if (post.getAuthor() != null) {
            return copyUserBrief(post.getAuthor());
        }
        return sanitizeAuthor(viewerUid, post.getAuthorId(), authors.get(post.getAuthorId()));
    }

    private PageResult<CrossDomainRecommendationVO> assembleCrossDomainPage(List<PostBriefDTO> posts,
                                                                            Long viewerUid,
                                                                            Integer sourceDomain,
                                                                            UserIntentDTO intent,
                                                                            Map<Long, Long> publicPostCountByAuthor,
                                                                            boolean hasMore,
                                                                            String source,
                                                                            boolean degraded,
                                                                            String fallbackReason) {
        if (posts == null || posts.isEmpty()) {
            return PageResult.<CrossDomainRecommendationVO>empty()
                    .withMetadata(source, degraded, fallbackReason, null);
        }
        List<Long> postIds = posts.stream().map(PostBriefDTO::getId).toList();
        var counters = postFacade.batchGetCounters(postIds);
        var authors = userFacade.batchGetUserBriefs(posts.stream()
                .map(PostBriefDTO::getAuthorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        List<CrossDomainRecommendationVO> items = posts.stream().map(post -> {
            PostCounterDTO counter = counters.get(post.getId());
            List<String> reasons = recommendationReasons(post, counter, intent, publicPostCountByAuthor);
            Integer targetDomain = effectiveDomain(post.getDomain());
            FeedItemVO feedItem = FeedItemVO.builder()
                    .post(post)
                    .author(feedAuthor(post, authors, viewerUid))
                    .counter(counter)
                    .recommendationReasons(reasons)
                    .myInteraction(viewerUid == null ? null : FeedItemVO.MyInteraction.builder()
                            .liked(interactionFacade.hasLiked(viewerUid, post.getId()))
                            .favorited(interactionFacade.hasFavorited(viewerUid, post.getId()))
                            .build())
                    .build();
            return CrossDomainRecommendationVO.builder()
                    .item(feedItem)
                    .sourceDomain(sourceDomain)
                    .sourceDomainName(domainName(sourceDomain))
                    .targetDomain(targetDomain)
                    .targetDomainName(domainName(targetDomain))
                    .recommendationReason(neutralRecommendationReason(crossDomainReason(post, sourceDomain, targetDomain, reasons, null, intent), post))
                    .degraded(degraded)
                    .build();
        }).toList();
        String nextCursor = hasMore ? crossDomainNextCursor(posts) : null;
        return PageResult.of(items, nextCursor, hasMore)
                .withMetadata(source, degraded, fallbackReason, null);
    }

    private PageResult<FeedItemVO> assembleRecommendPosts(List<PostBriefDTO> posts,
                                                          Long viewerUid,
                                                          String nextCursor,
                                                          boolean hasMore,
                                                          UserIntentDTO intent,
                                                          Map<Long, Long> publicPostCountByAuthor) {
        if (posts == null || posts.isEmpty()) return PageResult.empty();
        List<Long> postIds = posts.stream().map(PostBriefDTO::getId).toList();
        var counters = postFacade.batchGetCounters(postIds);
        var authors = userFacade.batchGetUserBriefs(posts.stream()
                .map(PostBriefDTO::getAuthorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        List<FeedItemVO> items = posts.stream().map(p -> FeedItemVO.builder()
                .post(p)
                .author(feedAuthor(p, authors, viewerUid))
                .counter(counters.get(p.getId()))
                .recommendationReasons(recommendationReasons(p, counters.get(p.getId()), intent, publicPostCountByAuthor))
                .myInteraction(viewerUid == null ? null : FeedItemVO.MyInteraction.builder()
                        .liked(interactionFacade.hasLiked(viewerUid, p.getId()))
                        .favorited(interactionFacade.hasFavorited(viewerUid, p.getId()))
                        .build())
                .build()).toList();
        return PageResult.of(items, nextCursor, hasMore);
    }

    private UserBriefDTO sanitizeAuthor(Long viewerUid, Long targetUid, UserBriefDTO dto) {
        UserBriefDTO copy = copyUserBrief(dto);
        if (copy == null) {
            return null;
        }
        Long effectiveTargetUid = targetUid != null ? targetUid : copy.getUid();
        if (effectiveTargetUid == null) {
            copy.setProfileVisible(false);
            copy.setIntentVisible(false);
            copy.setIsFollowing(false);
            return copy;
        }
        boolean profileVisible = userFacade.isProfileVisible(viewerUid, effectiveTargetUid);
        copy.setProfileVisible(profileVisible);
        copy.setIntentVisible(userFacade.isIntentVisible(viewerUid, effectiveTargetUid));
        if (viewerUid != null && effectiveTargetUid != null && !viewerUid.equals(effectiveTargetUid)) {
            copy.setIsFollowing(userFacade.isFollowing(viewerUid, effectiveTargetUid));
        }
        if (!profileVisible) {
            copy.setNickname("");
            copy.setAvatarUrl("");
            copy.setBio("");
            copy.setFollowerCount(0L);
            copy.setFollowingCount(0L);
            copy.setPostCount(0L);
            copy.setPrivacyReason("PROFILE_RESTRICTED");
        }
        return copy;
    }

    private static UserBriefDTO copyUserBrief(UserBriefDTO dto) {
        if (dto == null) {
            return null;
        }
        return UserBriefDTO.builder()
                .uid(dto.getUid())
                .nickname(dto.getNickname())
                .avatarUrl(dto.getAvatarUrl())
                .bio(dto.getBio())
                .followerCount(dto.getFollowerCount())
                .followingCount(dto.getFollowingCount())
                .postCount(dto.getPostCount())
                .isFollowing(dto.getIsFollowing())
                .profileVisible(dto.getProfileVisible())
                .intentVisible(dto.getIntentVisible())
                .privacyReason(dto.getPrivacyReason())
                .build();
    }

    private PageResult<CrossDomainRecommendationVO> fallbackCrossDomainRecommendations(Long uid,
                                                                                       String cursor,
                                                                                       int size,
                                                                                       Integer sourceDomain) {
        PageResult<FeedItemVO> hotPage = getHotFeed(uid, cursor, size, null);
        if (hotPage != null && hotPage.getItems() != null && !hotPage.getItems().isEmpty()) {
            return wrapFallbackCrossDomainPage(hotPage, sourceDomain, "hot", "跨领域候选不足，已回退到热门内容");
        }
        PageResult<FeedItemVO> latestPage = getLatestFeed(uid, cursor, size, null);
        if (latestPage != null && latestPage.getItems() != null && !latestPage.getItems().isEmpty()) {
            return wrapFallbackCrossDomainPage(latestPage, sourceDomain, "latest", "跨领域候选不足，已回退到最新内容");
        }
        return PageResult.<CrossDomainRecommendationVO>empty()
                .withMetadata("cross-domain-empty", true, "跨领域候选不足，且无可用回退内容", null);
    }

    private PageResult<CrossDomainRecommendationVO> wrapFallbackCrossDomainPage(PageResult<FeedItemVO> page,
                                                                                 Integer sourceDomain,
                                                                                 String source,
                                                                                 String fallbackReason) {
        if (page == null || page.getItems() == null || page.getItems().isEmpty()) {
            return PageResult.<CrossDomainRecommendationVO>empty()
                    .withMetadata(source, true, fallbackReason, null);
        }
        List<CrossDomainRecommendationVO> items = page.getItems().stream()
                .filter(Objects::nonNull)
                .map(item -> {
                    Integer targetDomain = effectiveDomain(item.getPost() == null ? null : item.getPost().getDomain());
                    return CrossDomainRecommendationVO.builder()
                            .item(item)
                            .sourceDomain(sourceDomain)
                            .sourceDomainName(domainName(sourceDomain))
                            .targetDomain(targetDomain)
                            .targetDomainName(domainName(targetDomain))
                            .recommendationReason(neutralRecommendationReason(fallbackReason, item.getPost()))
                            .degraded(true)
                            .build();
                })
                .toList();
        return PageResult.of(items, page.getNextCursor(), Boolean.TRUE.equals(page.getHasMore()))
                .withMetadata(source, true, fallbackReason, null);
    }

    private double recommendScore(PostBriefDTO post, UserIntentDTO intent, Map<Long, Long> publicPostCountByAuthor) {
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
        return heat + recency + tagBonus + intentScore(post, intent) + newCreatorBoost(post, publicPostCountByAuthor);
    }

    private double crossDomainScore(PostBriefDTO post,
                                    UserIntentDTO intent,
                                    Map<Long, Long> publicPostCountByAuthor,
                                    Integer sourceDomain) {
        return recommendScore(post, intent, publicPostCountByAuthor)
                + crossDomainBridgeBoost(post, sourceDomain, effectiveDomain(post == null ? null : post.getDomain()), intent);
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
            score += 72D;
        }
        return score;
    }

    private List<String> recommendationReasons(PostBriefDTO post,
                                               PostCounterDTO counter,
                                               UserIntentDTO intent,
                                               Map<Long, Long> publicPostCountByAuthor) {
        List<String> reasons = new ArrayList<>();
        if (isNewCreator(post, publicPostCountByAuthor)) {
            reasons.add(NEW_CREATOR_SUPPORT_REASON);
        }
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
            reasons.add("根据近期活跃度、发布时间和标签完整度推荐");
        }
        return reasons.stream()
                .map(reason -> neutralRecommendationReason(reason, post))
                .distinct()
                .limit(3)
                .toList();
    }

    private List<PostBriefDTO> visiblePostsForViewer(List<PostBriefDTO> posts, Long viewerUid) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        List<PostBriefDTO> distributablePosts = filterDistributablePosts(posts);
        Set<Long> hiddenPostIds = hiddenPostIdsSafely(viewerUid);
        if (hiddenPostIds.isEmpty()) {
            return distributablePosts;
        }
        return distributablePosts.stream()
                .filter(post -> post != null && !hiddenPostIds.contains(post.getId()))
                .toList();
    }

    private List<PostBriefDTO> filterDistributablePosts(List<PostBriefDTO> posts) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        return posts.stream()
                .filter(PublicContentFilter::isDistributablePost)
                .toList();
    }

    private String neutralRecommendationReason(String reason, PostBriefDTO post) {
        String value = clean(reason);
        if (value.isBlank()) {
            return "近期社区互动热度较高";
        }
        if (PublicContentFilter.isUnsafeSuggestionText(value) || isHighRiskDomain(post)) {
            if (value.contains("编辑") || value.contains("精选")) {
                return "频道编辑整理";
            }
            if (value.contains("互动") || value.contains("热度") || value.contains("讨论")) {
                return "近期社区互动热度较高";
            }
            return "同频道近期讨论较多";
        }
        return value.length() > 32 ? value.substring(0, 32) : value;
    }

    private boolean isHighRiskDomain(PostBriefDTO post) {
        return post != null && Objects.equals(effectiveDomain(post.getDomain()), Post.DOMAIN_INVESTMENT);
    }

    private Set<Long> hiddenPostIdsSafely(Long viewerUid) {
        if (viewerUid == null) {
            return Set.of();
        }
        return feedbackStore.hiddenPostIds(viewerUid);
    }

    private double newCreatorBoost(PostBriefDTO post, Map<Long, Long> publicPostCountByAuthor) {
        return isNewCreator(post, publicPostCountByAuthor) ? NEW_CREATOR_BOOST_SCORE : 0D;
    }

    private boolean isNewCreator(PostBriefDTO post, Map<Long, Long> publicPostCountByAuthor) {
        if (post == null || post.getAuthorId() == null || publicPostCountByAuthor == null || publicPostCountByAuthor.isEmpty()) {
            return false;
        }
        Long publicPostCount = publicPostCountByAuthor.get(post.getAuthorId());
        return publicPostCount != null && publicPostCount > 0 && publicPostCount <= NEW_CREATOR_MAX_PUBLIC_POSTS;
    }

    private void recordRecommendFeedNewCreatorSupportStatsSafely(Long viewerUid,
                                                                 Integer domain,
                                                                 List<PostBriefDTO> deliveredPosts,
                                                                 Map<Long, Long> publicPostCountByAuthor) {
        if (deliveredPosts == null || deliveredPosts.isEmpty()) {
            return;
        }
        int deliveredItemCount = deliveredPosts.size();
        int supportHitItemCount = (int) deliveredPosts.stream()
                .filter(post -> isNewCreator(post, publicPostCountByAuthor))
                .count();
        try {
            recommendFeedNewCreatorSupportRecorder.recordRecommendFeedResponse(
                    viewerUid,
                    domain,
                    deliveredItemCount,
                    supportHitItemCount);
        } catch (RuntimeException e) {
            log.warn("recommend feed new creator support stats skipped, viewerUid={}, domain={}, deliveredItemCount={}, supportHitItemCount={}",
                    viewerUid, domain, deliveredItemCount, supportHitItemCount, e);
        }
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

    private String crossDomainReason(PostBriefDTO post,
                                     Integer sourceDomain,
                                     Integer targetDomain,
                                     List<String> baseReasons,
                                     String fallbackReason,
                                     UserIntentDTO intent) {
        if (fallbackReason != null && !fallbackReason.isBlank()) {
            return fallbackReason;
        }
        JsonNode ext = parseExt(post == null ? null : post.getExtJson());
        String content = searchableText(post, ext);
        String matchedInterest = intent == null ? "" : firstMatchedInterest(content, interestCandidates(intent));
        String topic = firstNonBlank(ext.path("topic").asText(""), ext.path("category").asText(""));
        if (!matchedInterest.isBlank()) {
            return "从" + domainName(sourceDomain) + "延伸到" + domainName(targetDomain) + "，匹配你关注的" + matchedInterest;
        }
        if (!topic.isBlank()) {
            return "从" + domainName(sourceDomain) + "延伸到" + domainName(targetDomain) + "，覆盖话题：" + topic;
        }
        if (baseReasons != null && !baseReasons.isEmpty()) {
            return baseReasons.get(0);
        }
        return "来自" + domainName(targetDomain) + "领域的延伸内容";
    }

    private double crossDomainBridgeBoost(PostBriefDTO post,
                                          Integer sourceDomain,
                                          Integer targetDomain,
                                          UserIntentDTO intent) {
        if (post == null || intent == null) {
            return 0D;
        }
        JsonNode ext = parseExt(post.getExtJson());
        String content = searchableText(post, ext);
        double score = 0D;
        if (Objects.equals(sourceDomain, Post.DOMAIN_TECH) && Objects.equals(targetDomain, Post.DOMAIN_CAREER)) {
            if (matchesAny(content, intent.getTargetPositions()) || matchesAny(content, intent.getTargetCompanies())) {
                score += 10D;
            }
        }
        if (Objects.equals(sourceDomain, Post.DOMAIN_CAREER) && Objects.equals(targetDomain, Post.DOMAIN_TECH)) {
            if (matchesAny(content, intent.getTechStack())) {
                score += 10D;
            }
        }
        if (Objects.equals(targetDomain, Post.DOMAIN_READING)
                || Objects.equals(targetDomain, Post.DOMAIN_LIFESTYLE)
                || Objects.equals(targetDomain, Post.DOMAIN_INVESTMENT)) {
            if (!firstMatchedInterest(content, interestCandidates(intent)).isBlank()) {
                score += 8D;
            }
        }
        return score;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static long safe(Long value) {
        return value == null ? 0L : value;
    }

    private int overFetchSize(int size) {
        return Math.min(Math.max(size * 5, size), 100);
    }

    private int crossDomainCandidateFetchSize(int size) {
        return Math.min(Math.max(size * 2, size), MAX_CROSS_DOMAIN_CANDIDATE_FETCH_SIZE);
    }

    private List<Integer> crossDomainTargetDomains(Integer sourceDomain) {
        return Stream.of(
                        Post.DOMAIN_TECH,
                        Post.DOMAIN_CAREER,
                        Post.DOMAIN_READING,
                        Post.DOMAIN_LIFESTYLE,
                        Post.DOMAIN_INVESTMENT)
                .filter(code -> !Objects.equals(code, sourceDomain))
                .toList();
    }

    private int inferCrossDomainSource(UserIntentDTO intent) {
        if (intent == null) {
            return Post.DOMAIN_TECH;
        }
        if (hasAnyValue(intent.getTechStack()) || hasAnyValue(intent.getTargetCompanies())) {
            return Post.DOMAIN_TECH;
        }
        if (hasAnyValue(intent.getTargetPositions()) || !clean(intent.getExpectedCity()).isBlank() || intent.getExpectedSalaryRange() != null) {
            return Post.DOMAIN_CAREER;
        }
        if (intentKeywords(intent, List.of("阅读", "读书", "书单", "拆书"))) {
            return Post.DOMAIN_READING;
        }
        if (intentKeywords(intent, List.of("生活", "租房", "通勤", "健身", "旅行"))) {
            return Post.DOMAIN_LIFESTYLE;
        }
        if (intentKeywords(intent, List.of("投资", "理财", "基金", "股票"))) {
            return Post.DOMAIN_INVESTMENT;
        }
        return Post.DOMAIN_TECH;
    }

    private boolean intentKeywords(UserIntentDTO intent, List<String> keywords) {
        if (intent == null || keywords == null || keywords.isEmpty()) {
            return false;
        }
        String text = Stream.of(
                        intent.getInterestTopics(),
                        intent.getInterestTags(),
                        intent.getContentPreferences(),
                        intent.getTechStack(),
                        intent.getTargetCompanies(),
                        intent.getTargetPositions())
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
        String normalized = clean(text);
        return keywords.stream().anyMatch(keyword -> normalized.contains(keyword));
    }

    private boolean hasAnyValue(List<String> items) {
        return items != null && items.stream().anyMatch(item -> item != null && !item.isBlank());
    }

    private Integer effectiveDomain(Integer domain) {
        return domain == null ? Post.DOMAIN_TECH : domain;
    }

    private String domainName(Integer domain) {
        return switch (effectiveDomain(domain)) {
            case Post.DOMAIN_CAREER -> "职场";
            case Post.DOMAIN_READING -> "阅读";
            case Post.DOMAIN_LIFESTYLE -> "生活";
            case Post.DOMAIN_INVESTMENT -> "投资理财";
            default -> "技术";
        };
    }

    private String crossDomainNextCursor(List<PostBriefDTO> posts) {
        return posts.stream()
                .map(PostBriefDTO::getCreateTime)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .map(time -> String.valueOf(time.toInstant(java.time.ZoneOffset.UTC).toEpochMilli()))
                .orElse(null);
    }

    private boolean matchesDomain(FeedItemVO item, Integer domain) {
        if (item == null || item.getPost() == null) {
            return false;
        }
        return effectiveDomain(item.getPost().getDomain()) == domain;
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
