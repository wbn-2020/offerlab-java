package com.offerlab.community.feed.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.utils.CursorUtils;
import com.offerlab.community.common.utils.LogMask;
import com.offerlab.community.feed.api.FeedFacade;
import com.offerlab.community.feed.api.control.UserDistributionControlsQueryFacade;
import com.offerlab.community.feed.api.control.UserDistributionControlsSnapshot;
import com.offerlab.community.feed.api.quality.ChannelQualitySignalSummary;
import com.offerlab.community.feed.api.quality.CreatorQualitySignalQueryFacade;
import com.offerlab.community.feed.api.quality.CreatorQualitySignalQueryResult;
import com.offerlab.community.feed.api.dto.ChannelHotBoardItemVO;
import com.offerlab.community.feed.api.dto.ChannelHotBoardVO;
import com.offerlab.community.feed.api.dto.CrossDomainRecommendationVO;
import com.offerlab.community.feed.api.dto.FeedControlVO;
import com.offerlab.community.feed.api.dto.FeedFeedbackPreferenceVO;
import com.offerlab.community.feed.api.dto.FeedItemVO;
import com.offerlab.community.feed.api.dto.RecommendationReasonDetailVO;
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
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedFacadeImpl implements FeedFacade, UserDistributionControlsQueryFacade, CreatorQualitySignalQueryFacade {

    private static final int MAX_DOMAIN_INBOX_SCAN_ROWS = 1000;
    private static final int MAX_FOLLOWING_DB_SCAN_ROWS = 5000;
    private static final int MAX_CROSS_DOMAIN_CANDIDATE_FETCH_SIZE = 100;
    private static final int AUTHOR_CONTROL_PUBLIC_POST_SCAN_SIZE = 50;
    private static final String CROSS_DOMAIN_CURSOR_VERSION = "cd1";
    private static final String FOLLOWING_DB_CURSOR_VERSION = "fdb1";
    private static final long NEW_CREATOR_MAX_PUBLIC_POSTS = 3L;
    private static final double NEW_CREATOR_BOOST_SCORE = 12D;
    private static final double LESS_LIKE_THIS_PENALTY = 1_000D;
    private static final String NEW_CREATOR_SUPPORT_REASON = "新作者前 3 篇内容扶持";
    private static final String CHANNEL_HOT_BOARD_RULE_VERSION = "channel-hot.v1";
    private static final String CHANNEL_HOT_BOARD_REASON = "按公开互动与发布时间综合排序";
    private static final String RESERVED_FEEDBACK_REASON_PREFIX = "v30:";

    private final FeedInboxRedis feedRedis;
    private final FeedFeedbackStore feedbackStore;
    private final PostFacade postFacade;
    private final UserFacade userFacade;
    private final InteractionFacade interactionFacade;
    private final ObjectMapper objectMapper;
    private final RecommendFeedNewCreatorSupportRecorder recommendFeedNewCreatorSupportRecorder;

    @Value("${offerlab.kafka.enabled:true}")
    private boolean kafkaEnabled = true;

    @Value("${offerlab.feed.kafka-consumer-enabled:true}")
    private boolean feedKafkaConsumerEnabled = true;

    @Override
    public PageResult<FeedItemVO> getFollowingFeed(Long uid, String cursor, int size, Integer domain) {
        if (cursor != null && cursor.startsWith("db:")) {
            return fallbackFollowingFromDb(uid, cursor.substring(3), size, domain);
        }
        if (!kafkaEnabled || !feedKafkaConsumerEnabled) {
            return fallbackFollowingFromDb(uid, cursor, size, domain);
        }
        double maxScore = parseCursorScore(cursor);
        if (domain != null) {
            return getDomainFollowingFeed(uid, cursor, maxScore, size, domain);
        }
        Set<ZSetOperations.TypedTuple<String>> tuples = readFollowingInboxSafely(uid, maxScore, size);
        if (tuples == null) {
            return fallbackFollowingFromDb(uid, cursor, size, null);
        }
        return assembleFromTuples(tuples, size, uid, FeedSource.FOLLOWING);
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
        Set<Long> blockedAuthorIds = blockedAuthorIdsSafely(uid);
        Set<Integer> reducedDomains = feedbackStore.lessLikedDomains(uid);
        List<PostBriefDTO> visibleCandidates = filterFeedCandidates(
                page.getItems(), hiddenPostIds, blockedAuthorIds, domain == null);
        Map<Long, Long> publicPostCountByAuthor = postFacade.batchCountPublicPublishedPostsByAuthors(visibleCandidates.stream()
                .map(PostBriefDTO::getAuthorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        List<PostBriefDTO> ranked = visibleCandidates.stream()
                .sorted(Comparator.<PostBriefDTO>comparingDouble(
                                post -> recommendScore(post, intent, publicPostCountByAuthor, reducedDomains)).reversed()
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
        Integer sourceDomain = inferCrossDomainSource(intent);
        Set<Long> hiddenPostIds = uid == null ? Set.of() : feedbackStore.hiddenPostIds(uid);
        Set<Long> blockedAuthorIds = blockedAuthorIdsSafely(uid);
        List<PostBriefDTO> candidates = new ArrayList<>();
        List<String> failedDomains = new ArrayList<>();
        CrossDomainCursor pageCursor = parseCrossDomainCursor(cursor);
        int fetchSize = crossDomainCandidateFetchSize(pageSize);
        boolean sourceHasMore = false;
        for (Integer targetDomain : crossDomainTargetDomains(sourceDomain)) {
            try {
                PageResult<PostBriefDTO> page = postFacade.listPostsByKeyset(
                        null, null, null, null, targetDomain, pageCursor.time(), pageCursor.id(), fetchSize);
                if (page == null || page.getItems() == null || page.getItems().isEmpty()) {
                    sourceHasMore |= page != null && Boolean.TRUE.equals(page.getHasMore());
                    continue;
                }
                filterFeedCandidates(page.getItems(), hiddenPostIds, blockedAuthorIds, true)
                        .forEach(candidates::add);
                sourceHasMore |= Boolean.TRUE.equals(page.getHasMore());
            } catch (RuntimeException e) {
                failedDomains.add(domainName(targetDomain));
                log.warn("cross-domain recommendation candidate load failed, viewerUid={}, targetDomain={}",
                        LogMask.id(uid), targetDomain, e);
            }
        }
        if (!candidates.isEmpty()) {
            Map<Long, Long> publicPostCountByAuthor = postFacade.batchCountPublicPublishedPostsByAuthors(candidates.stream()
                    .map(PostBriefDTO::getAuthorId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet()));
            List<PostBriefDTO> ranked = candidates.stream()
                    // The cursor is a post keyset cursor, so the page order must use the same keyset.
                    .sorted(Comparator.comparing(PostBriefDTO::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(PostBriefDTO::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(pageSize)
                    .toList();
            String fallbackReason = failedDomains.isEmpty()
                    ? null
                    : "部分跨领域候选拉取失败：" + String.join("、", failedDomains);
            boolean hasMore = failedDomains.isEmpty()
                    && (sourceHasMore || candidates.size() > ranked.size());
            String nextCursor = hasMore && !ranked.isEmpty()
                    ? crossDomainCursor(ranked.get(ranked.size() - 1))
                    : null;
            return assembleCrossDomainPage(
                    ranked,
                    uid,
                    sourceDomain,
                    intent,
                    publicPostCountByAuthor,
                    hasMore,
                    nextCursor,
                    failedDomains.isEmpty() ? "cross-domain" : "cross-domain-partial",
                    !failedDomains.isEmpty(),
                    fallbackReason);
        }
        if (pageCursor.present()) {
            return PageResult.<CrossDomainRecommendationVO>empty()
                    .withMetadata("cross-domain", !failedDomains.isEmpty(),
                            failedDomains.isEmpty() ? null : "部分跨领域候选拉取失败：" + String.join("、", failedDomains),
                            null);
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
            return assembleFromPosts(
                    page.getItems(), viewerUid, page.getNextCursor(), Boolean.TRUE.equals(page.getHasMore()), FeedSource.LATEST);
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
        return assembleFromTuples(tuples, size, viewerUid, FeedSource.LATEST);
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
        PageResult<FeedItemVO> redisPage = assembleFromTuples(tuples, size, viewerUid, FeedSource.LATEST);
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

    private Set<ZSetOperations.TypedTuple<String>> readFollowingInboxSafely(Long uid, double maxScore, int size) {
        try {
            return feedRedis.readInboxWithScore(uid, maxScore, size);
        } catch (Exception e) {
            log.warn("feed redis following inbox read failed, uid={}, fallback to database scan: {}",
                    LogMask.id(uid), e.toString());
            return null;
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
            return FeedInboxRedis.scoreTimestamp(redisScore);
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
            return assembleFromPosts(
                    page.getItems(), viewerUid, page.getNextCursor(), Boolean.TRUE.equals(page.getHasMore()), FeedSource.HOT);
        }
        var page = postFacade.getHot(cursor, size);
        if (page == null || page.getItems() == null || page.getItems().isEmpty()) {
            return getLatestFeed(viewerUid, cursor, size, null);
        }
        return assembleFromPosts(
                page.getItems(), viewerUid, page.getNextCursor(), Boolean.TRUE.equals(page.getHasMore()), FeedSource.HOT);
    }

    @Override
    public void recordFeedback(Long uid, Long postId, String action, String reason) {
        recordFeedback(uid, postId, action, reason, null);
    }

    @Override
    public void recordFeedback(Long uid, Long postId, String action, String reason, String reasonCode) {
        if (uid == null || uid <= 0 || postId == null || postId <= 0 || action == null || action.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        rejectReservedFeedbackReason(reason);
        if (isRestoreAction(action)) {
            feedbackStore.record(uid, postId, "RESTORE", reason, null);
            return;
        }
        // Feedback is accepted only for content visible to an anonymous public reader.
        Map<Long, PostBriefDTO> posts = postFacade.batchGetPosts(List.of(postId), null);
        PostBriefDTO post = posts == null ? null : posts.get(postId);
        if (!PublicContentFilter.isDistributablePost(post)) {
            throw new BizException(ErrorCode.POST_NOT_FOUND);
        }
        if (isLegacyHideAuthorAction(action)) {
            if (!isBlockablePublicAuthor(post, uid)) {
                throw new BizException(ErrorCode.POST_NOT_FOUND);
            }
            feedbackStore.blockAuthor(uid, post.getAuthorId());
            return;
        }
        feedbackStore.record(uid, postId, action,
                normalizeFeedbackReason(action, reason, reasonCode), effectiveDomain(post.getDomain()));
    }

    @Override
    public ChannelHotBoardVO getChannelHotBoard(Long viewerUid, Integer domain, int size) {
        if (domain == null || effectiveDomain(domain) == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        int boardSize = Math.min(Math.max(size, 1), 20);
        PageResult<FeedItemVO> page = getHotFeed(viewerUid, null, boardSize, domain);
        List<FeedItemVO> entries = page == null || page.getItems() == null ? List.of() : page.getItems();
        List<ChannelHotBoardItemVO> items = new ArrayList<>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            FeedItemVO item = entries.get(index);
            if (item == null || item.getPost() == null) {
                continue;
            }
            items.add(ChannelHotBoardItemVO.builder()
                    .rank(items.size() + 1)
                    .item(item)
                    .reasonText(CHANNEL_HOT_BOARD_REASON)
                    .build());
        }
        return ChannelHotBoardVO.builder()
                .domain(domain)
                .ruleVersion(CHANNEL_HOT_BOARD_RULE_VERSION)
                .generatedAt(LocalDateTime.now())
                .items(items)
                .build();
    }

    @Override
    public UserDistributionControlsSnapshot snapshot(Long viewerUid) {
        if (viewerUid == null || viewerUid <= 0) {
            return UserDistributionControlsSnapshot.emptyAvailable();
        }
        Set<Long> hiddenPostIds = hiddenPostIdsSafely(viewerUid);
        Set<Integer> reducedDomains = lessLikedDomainsSafely(viewerUid);
        if (!feedbackStore.controlsAvailable()) {
            return new UserDistributionControlsSnapshot(hiddenPostIds, Set.of(), reducedDomains, false);
        }
        return new UserDistributionControlsSnapshot(
                hiddenPostIds,
                feedbackStore.blockedAuthorIds(viewerUid),
                reducedDomains,
                true);
    }

    @Override
    public CreatorQualitySignalQueryResult findActiveQualitySignals(
            java.util.Collection<Long> publicPostIds,
            LocalDateTime since,
            LocalDateTime now
    ) {
        return feedbackStore.findActiveQualitySignals(publicPostIds, since, now);
    }

    @Override
    public ChannelQualitySignalSummary findChannelQualitySignals(
            java.util.Collection<Integer> domainCodes,
            int minimumDistinctReaders,
            LocalDateTime since,
            LocalDateTime now
    ) {
        return feedbackStore.findChannelQualitySignals(domainCodes, minimumDistinctReaders, since, now);
    }

    @Override
    public PageResult<FeedFeedbackPreferenceVO> listFeedbackPreferences(Long uid, String cursor, int size) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return feedbackStore.list(uid, cursor, size);
    }

    @Override
    public FeedFeedbackPreferenceVO getFeedbackPreference(Long uid, Long postId) {
        if (uid == null || uid <= 0 || postId == null || postId <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return feedbackStore.find(uid, postId);
    }

    @Override
    public FeedControlVO blockAuthor(Long uid, Long authorUid) {
        if (uid == null || uid <= 0 || authorUid == null || authorUid <= 0 || Objects.equals(uid, authorUid)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (!hasKnownPublicNonAnonymousPost(authorUid)) {
            throw new BizException(ErrorCode.POST_NOT_FOUND);
        }
        return feedbackStore.blockAuthor(uid, authorUid);
    }

    @Override
    public void unblockAuthor(Long uid, Long authorUid) {
        if (uid == null || uid <= 0 || authorUid == null || authorUid <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        feedbackStore.unblockAuthor(uid, authorUid);
    }

    @Override
    public PageResult<FeedControlVO> listControls(Long uid, String cursor, int size) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return feedbackStore.listControls(uid, cursor, size);
    }

    @Override
    public void deleteControl(Long uid, Long controlId) {
        if (uid == null || uid <= 0 || controlId == null || controlId <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        feedbackStore.deleteControl(uid, controlId);
    }

    private PageResult<FeedItemVO> assembleFromTuples(Set<ZSetOperations.TypedTuple<String>> tuples,
                                                      int size,
                                                      Long viewerUid,
                                                      FeedSource source) {
        if (tuples == null || tuples.isEmpty()) return PageResult.empty();
        List<long[]> idsAndScores = idsAndScores(tuples);
        if (idsAndScores.isEmpty()) {
            return PageResult.empty();
        }

        List<Long> postIds = idsAndScores.stream().map(a -> a[0]).toList();
        var posts = postFacade.batchGetPosts(postIds, viewerUid);
        Set<Long> hiddenPostIds = hiddenPostIdsSafely(viewerUid);
        Set<Long> blockedAuthorIds = blockedAuthorIdsSafely(viewerUid);
        var counters = postFacade.batchGetCounters(postIds);
        Set<Long> authorIds = posts.values().stream().map(PostBriefDTO::getAuthorId).collect(Collectors.toSet());
        var authors = userFacade.batchGetUserBriefs(authorIds);
        Set<Long> likedPostIds = viewerUid == null ? Set.of() : interactionFacade.likedPostIds(viewerUid, postIds);
        Set<Long> favoritedPostIds = viewerUid == null ? Set.of() : interactionFacade.favoritedPostIds(viewerUid, postIds);

        List<FeedItemVO> items = new ArrayList<>(postIds.size());
        for (long[] pair : idsAndScores) {
            PostBriefDTO p = posts.get(pair[0]);
            if (!isVisibleFeedCandidate(p, hiddenPostIds, blockedAuthorIds, false)) continue;
            UserBriefDTO author = feedAuthor(p, authors, viewerUid);
            PostCounterDTO counter = counters.get(p.getId());
            FeedItemVO.MyInteraction my = null;
            if (viewerUid != null) {
                my = myInteraction(p.getId(), likedPostIds, favoritedPostIds);
            }
            items.add(FeedItemVO.builder()
                    .post(p)
                    .author(author)
                    .counter(counter)
                    .myInteraction(my)
                    .recommendationReasonDetails(sourceReasonDetails(source))
                    .sourceType(source.type())
                    .sourceLabel(source.label())
                    .reasonCode(source.reasonCode())
                    .reasonText(source.reasonText())
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

    private PageResult<FeedItemVO> getDomainFollowingFeed(Long uid,
                                                         String cursor,
                                                         double maxScore,
                                                         int size,
                                                         Integer domain) {
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
            Set<ZSetOperations.TypedTuple<String>> tuples = readFollowingInboxSafely(uid, scanMaxScore, currentFetchSize);
            if (tuples == null) {
                return fallbackFollowingFromDb(uid, cursor, size, domain);
            }
            List<long[]> idsAndScores = idsAndScores(tuples);
            if (idsAndScores.isEmpty()) {
                sourceHasMore = false;
                break;
            }

            scannedRows += idsAndScores.size();
            lastRawScore = idsAndScores.get(idsAndScores.size() - 1)[1];
            sourceHasMore = idsAndScores.size() == currentFetchSize;

            PageResult<FeedItemVO> rawPage = assembleFromTuples(
                    tuples, currentFetchSize, uid, FeedSource.FOLLOWING);
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

    private PageResult<FeedItemVO> fallbackFollowingFromDb(Long uid,
                                                           String cursor,
                                                           int size,
                                                           Integer domain) {
        int pageSize = Math.max(1, size);
        CursorUtils.TimeIdCursor pageCursor = parseFollowingDbCursor(cursor);
        LocalDateTime scanTime = pageCursor.time();
        Long scanId = pageCursor.id();
        int scannedRows = 0;
        List<PostBriefDTO> matches = new ArrayList<>(pageSize + 1);
        Set<Long> hiddenPostIds = hiddenPostIdsSafely(uid);
        Set<Long> blockedAuthorIds = blockedAuthorIdsSafely(uid);
        boolean sourceHasMore = false;
        PostBriefDTO lastRawPost = null;

        while (matches.size() <= pageSize && scannedRows < MAX_FOLLOWING_DB_SCAN_ROWS) {
            int fetchSize = Math.min(
                    overFetchSize(pageSize),
                    MAX_FOLLOWING_DB_SCAN_ROWS - scannedRows);
            List<PostBriefDTO> raw = postFacade.listFollowingPostsByKeyset(
                    uid, domain, scanTime, scanId, fetchSize + 1);
            if (raw == null || raw.isEmpty()) {
                sourceHasMore = false;
                break;
            }

            sourceHasMore = raw.size() > fetchSize;
            List<PostBriefDTO> scanned = sourceHasMore ? raw.subList(0, fetchSize) : raw;
            scannedRows += scanned.size();
            for (PostBriefDTO post : scanned) {
                if (isVisibleFeedCandidate(post, hiddenPostIds, blockedAuthorIds, false)) {
                    matches.add(post);
                    if (matches.size() > pageSize) {
                        break;
                    }
                }
            }

            lastRawPost = scanned.get(scanned.size() - 1);
            if (matches.size() > pageSize || !sourceHasMore) {
                break;
            }
            if (!isStrictlyOlder(lastRawPost, scanTime, scanId)) {
                throw new IllegalStateException("following feed database cursor did not advance");
            }
            scanTime = lastRawPost.getCreateTime();
            scanId = lastRawPost.getId();
        }

        boolean hasMore = matches.size() > pageSize || sourceHasMore;
        List<PostBriefDTO> items = matches.size() > pageSize
                ? matches.subList(0, pageSize)
                : matches;
        PostBriefDTO cursorPost = matches.size() > pageSize && !items.isEmpty()
                ? items.get(items.size() - 1)
                : lastRawPost;
        String nextCursor = hasMore && cursorPost != null
                ? "db:" + followingDbCursor(cursorPost)
                : null;
        if (items.isEmpty()) {
            return PageResult.<FeedItemVO>of(List.of(), nextCursor, hasMore && nextCursor != null)
                    .withMetadata(
                            "following-db-fallback",
                            true,
                            "REDIS_FOLLOWING_UNAVAILABLE",
                            MAX_FOLLOWING_DB_SCAN_ROWS)
                    .withDiagnostic("followingFallbackScanRows", scannedRows)
                    .withDiagnostic("followingFallbackScanLimited",
                            scannedRows >= MAX_FOLLOWING_DB_SCAN_ROWS && sourceHasMore);
        }
        PageResult<FeedItemVO> result = assembleFromPosts(
                items,
                uid,
                nextCursor,
                hasMore && nextCursor != null,
                FeedSource.FOLLOWING);
        return result.withMetadata(
                        "following-db-fallback",
                        true,
                        "REDIS_FOLLOWING_UNAVAILABLE",
                        MAX_FOLLOWING_DB_SCAN_ROWS)
                .withDiagnostic("followingFallbackScanRows", scannedRows)
                .withDiagnostic("followingFallbackScanLimited",
                        scannedRows >= MAX_FOLLOWING_DB_SCAN_ROWS && sourceHasMore);
    }

    private long[] toPostIdAndScore(ZSetOperations.TypedTuple<String> tuple) {
        if (tuple == null || tuple.getValue() == null) {
            return null;
        }
        try {
            return new long[]{Long.parseLong(tuple.getValue()), tuple.getScore() == null ? 0L : tuple.getScore().longValue()};
        } catch (NumberFormatException e) {
            log.warn("feed redis tuple skipped: invalid postId={}", LogMask.id(tuple.getValue()));
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
        return assembleFromPosts(
                page.getItems(), viewerUid, page.getNextCursor(), Boolean.TRUE.equals(page.getHasMore()), FeedSource.LATEST);
    }

    private PageResult<FeedItemVO> assembleFromPosts(List<PostBriefDTO> posts,
                                                     Long viewerUid,
                                                     String nextCursor,
                                                     boolean hasMore,
                                                     FeedSource source) {
        if (posts == null || posts.isEmpty()) return PageResult.empty();
        List<PostBriefDTO> visiblePosts = filterFeedCandidates(posts, viewerUid, false);
        if (visiblePosts.isEmpty()) return PageResult.empty();
        List<Long> postIds = visiblePosts.stream().map(PostBriefDTO::getId).toList();
        var counters = postFacade.batchGetCounters(postIds);
        var authors = userFacade.batchGetUserBriefs(visiblePosts.stream()
                .map(PostBriefDTO::getAuthorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        Set<Long> likedPostIds = viewerUid == null ? Set.of() : interactionFacade.likedPostIds(viewerUid, postIds);
        Set<Long> favoritedPostIds = viewerUid == null ? Set.of() : interactionFacade.favoritedPostIds(viewerUid, postIds);
        List<FeedItemVO> items = visiblePosts.stream().map(p -> FeedItemVO.builder()
                .post(p)
                .author(feedAuthor(p, authors, viewerUid))
                .counter(counters.get(p.getId()))
                .myInteraction(viewerUid == null ? null : myInteraction(p.getId(), likedPostIds, favoritedPostIds))
                .recommendationReasonDetails(sourceReasonDetails(source))
                .sourceType(source.type())
                .sourceLabel(source.label())
                .reasonCode(source.reasonCode())
                .reasonText(source.reasonText())
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
                                                                              String nextCursor,
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
        Set<Long> likedPostIds = viewerUid == null ? Set.of() : interactionFacade.likedPostIds(viewerUid, postIds);
        Set<Long> favoritedPostIds = viewerUid == null ? Set.of() : interactionFacade.favoritedPostIds(viewerUid, postIds);
        List<CrossDomainRecommendationVO> items = posts.stream().map(post -> {
            PostCounterDTO counter = counters.get(post.getId());
            List<String> reasons = recommendationReasons(post, counter, intent, publicPostCountByAuthor);
            Integer targetDomain = effectiveDomain(post.getDomain());
            String reasonText = crossDomainReason(sourceDomain, targetDomain, reasons, null);
            FeedItemVO feedItem = FeedItemVO.builder()
                    .post(post)
                    .author(feedAuthor(post, authors, viewerUid))
                    .counter(counter)
                    .recommendationReasons(reasons)
                    .recommendationReasonDetails(recommendationReasonDetails(reasons, post))
                    .myInteraction(viewerUid == null ? null : myInteraction(post.getId(), likedPostIds, favoritedPostIds))
                    .sourceType(FeedSource.CROSS_DOMAIN.type())
                    .sourceLabel(FeedSource.CROSS_DOMAIN.label())
                    .reasonCode(FeedSource.CROSS_DOMAIN.reasonCode())
                    .reasonText(reasonText)
                    .build();
            return CrossDomainRecommendationVO.builder()
                    .item(feedItem)
                    .sourceDomain(sourceDomain)
                    .sourceDomainName(domainName(sourceDomain))
                    .targetDomain(targetDomain)
                    .targetDomainName(domainName(targetDomain))
                    .recommendationReason(reasonText)
                    .degraded(degraded)
                    .build();
        }).toList();
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
        Set<Long> likedPostIds = viewerUid == null ? Set.of() : interactionFacade.likedPostIds(viewerUid, postIds);
        Set<Long> favoritedPostIds = viewerUid == null ? Set.of() : interactionFacade.favoritedPostIds(viewerUid, postIds);
        List<FeedItemVO> items = posts.stream().map(p -> {
            List<String> reasons = recommendationReasons(
                    p, counters.get(p.getId()), intent, publicPostCountByAuthor);
            String reasonText = reasons.isEmpty()
                    ? FeedSource.RECOMMEND.reasonText()
                    : reasons.get(0);
            return FeedItemVO.builder()
                    .post(p)
                    .author(feedAuthor(p, authors, viewerUid))
                    .counter(counters.get(p.getId()))
                    .recommendationReasons(reasons)
                    .recommendationReasonDetails(recommendationReasonDetails(reasons, p))
                    .myInteraction(viewerUid == null ? null : myInteraction(p.getId(), likedPostIds, favoritedPostIds))
                    .sourceType(FeedSource.RECOMMEND.type())
                    .sourceLabel(FeedSource.RECOMMEND.label())
                    .reasonCode(FeedSource.RECOMMEND.reasonCode())
                    .reasonText(reasonText)
                    .build();
        }).toList();
        return PageResult.of(items, nextCursor, hasMore);
    }

    private FeedItemVO.MyInteraction myInteraction(Long postId, Set<Long> likedPostIds, Set<Long> favoritedPostIds) {
        return FeedItemVO.MyInteraction.builder()
                .liked(likedPostIds != null && likedPostIds.contains(postId))
                .favorited(favoritedPostIds != null && favoritedPostIds.contains(postId))
                .build();
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
                .filter(item -> !isHighRiskDomain(item.getPost()))
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

    private double recommendScore(PostBriefDTO post,
                                  UserIntentDTO intent,
                                  Map<Long, Long> publicPostCountByAuthor,
                                  Set<Integer> reducedDomains) {
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
        Integer recommendationDomain = effectiveDomain(post.getDomain());
        double preferencePenalty = recommendationDomain != null
                && reducedDomains != null
                && reducedDomains.contains(recommendationDomain)
                ? LESS_LIKE_THIS_PENALTY
                : 0D;
        return heat + recency + tagBonus + intentScore(post, intent)
                + newCreatorBoost(post, publicPostCountByAuthor) - preferencePenalty;
    }

    private double crossDomainScore(PostBriefDTO post,
                                    UserIntentDTO intent,
                                    Map<Long, Long> publicPostCountByAuthor,
                                    Integer sourceDomain,
                                    Set<Integer> reducedDomains) {
        return recommendScore(post, intent, publicPostCountByAuthor, reducedDomains)
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
            reasons.add("匹配你关注的兴趣方向");
        }
        if (intent != null && matchesAny(engineeringArea, intent.getTargetCompanies())) {
            reasons.add("关联你关注的工程场景");
        }
        if (intent != null && matchesAny(technicalScenario, intent.getTargetPositions())) {
            reasons.add("关联你关注的技术主题");
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

    private List<PostBriefDTO> filterFeedCandidates(List<PostBriefDTO> posts,
                                                     Long viewerUid,
                                                     boolean excludeHighRiskDomains) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        Set<Long> hiddenPostIds = hiddenPostIdsSafely(viewerUid);
        Set<Long> blockedAuthorIds = blockedAuthorIdsSafely(viewerUid);
        return filterFeedCandidates(posts, hiddenPostIds, blockedAuthorIds, excludeHighRiskDomains);
    }

    private List<PostBriefDTO> filterFeedCandidates(List<PostBriefDTO> posts,
                                                     Set<Long> hiddenPostIds,
                                                     Set<Long> blockedAuthorIds,
                                                     boolean excludeHighRiskDomains) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        return posts.stream()
                .filter(post -> isVisibleFeedCandidate(post, hiddenPostIds, blockedAuthorIds, excludeHighRiskDomains))
                .toList();
    }

    private List<RecommendationReasonDetailVO> recommendationReasonDetails(List<String> reasons, PostBriefDTO post) {
        if (reasons == null || reasons.isEmpty()) {
            return List.of();
        }
        return reasons.stream()
                .map(reason -> RecommendationReasonDetailVO.builder()
                        .code(recommendationReasonCode(reason))
                        .text(neutralRecommendationReason(reason, post))
                        .build())
                .filter(detail -> detail.getText() != null && !detail.getText().isBlank())
                .distinct()
                .limit(3)
                .toList();
    }

    private List<RecommendationReasonDetailVO> sourceReasonDetails(FeedSource source) {
        if (source == null) {
            return List.of();
        }
        return List.of(RecommendationReasonDetailVO.builder()
                .code(source.reasonCode())
                .text(source.reasonText())
                .build());
    }

    private String recommendationReasonCode(String reason) {
        String value = clean(reason);
        if (NEW_CREATOR_SUPPORT_REASON.equals(value)) {
            return "NEW_CREATOR_SUPPORT";
        }
        if (value.contains("兴趣方向")) {
            return "INTEREST_MATCH";
        }
        if (value.contains("工程场景")) {
            return "ENGINEERING_CONTEXT";
        }
        if (value.contains("技术主题")) {
            return "TOPIC_MATCH";
        }
        if (value.contains("技术栈")) {
            return "TECH_STACK_MATCH";
        }
        if (value.contains("互动") || value.contains("热度")) {
            return "PUBLIC_ENGAGEMENT";
        }
        if (value.contains("新发布")) {
            return "RECENT_PUBLICATION";
        }
        if (value.contains("标签")) {
            return "TAG_COMPLETENESS";
        }
        return "RECENT_ACTIVITY";
    }

    private String normalizeFeedbackReason(String action, String reason, String reasonCode) {
        String code = clean(reasonCode).toUpperCase(Locale.ROOT);
        if (code.isBlank() || !isNegativeFeedbackAction(action)) {
            return reason;
        }
        if (!Set.of("NOT_RELEVANT", "TOO_FREQUENT", "ALREADY_KNOWN", "QUALITY_NOT_EXPECTED", "OTHER").contains(code)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return "v30:" + code.toLowerCase(Locale.ROOT);
    }

    private static void rejectReservedFeedbackReason(String reason) {
        String value = clean(reason);
        if (value.regionMatches(true, 0,
                RESERVED_FEEDBACK_REASON_PREFIX, 0, RESERVED_FEEDBACK_REASON_PREFIX.length())) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private static boolean isNegativeFeedbackAction(String action) {
        if (action == null) {
            return false;
        }
        String value = action.trim().toUpperCase(Locale.ROOT);
        return "HIDE".equals(value)
                || "LESS_LIKE_THIS".equals(value)
                || "NOT_INTERESTED".equals(value)
                || "DISLIKE".equals(value);
    }

    private boolean isVisibleFeedCandidate(PostBriefDTO post,
                                           Set<Long> hiddenPostIds,
                                           Set<Long> blockedAuthorIds,
                                           boolean excludeHighRiskDomains) {
        return PublicContentFilter.isDistributablePost(post)
                && (hiddenPostIds == null || !hiddenPostIds.contains(post.getId()))
                && (blockedAuthorIds == null || !blockedAuthorIds.contains(post.getAuthorId()))
                && (!excludeHighRiskDomains || !isHighRiskDomain(post));
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
        return post != null && isHighRiskDomain(post.getDomain());
    }

    private boolean isHighRiskDomain(Integer domain) {
        return Objects.equals(effectiveDomain(domain), Post.DOMAIN_INVESTMENT);
    }

    private Set<Long> hiddenPostIdsSafely(Long viewerUid) {
        if (viewerUid == null) {
            return Set.of();
        }
        try {
            return feedbackStore.hiddenPostIds(viewerUid);
        } catch (RuntimeException e) {
            log.debug("feed hidden control snapshot unavailable, viewerUid={}", LogMask.id(viewerUid), e);
            return Set.of();
        }
    }

    private Set<Integer> lessLikedDomainsSafely(Long viewerUid) {
        if (viewerUid == null) {
            return Set.of();
        }
        try {
            return feedbackStore.lessLikedDomains(viewerUid);
        } catch (RuntimeException e) {
            log.debug("feed reduced-domain control snapshot unavailable, viewerUid={}", LogMask.id(viewerUid), e);
            return Set.of();
        }
    }

    private Set<Long> blockedAuthorIdsSafely(Long viewerUid) {
        if (viewerUid == null) {
            return Set.of();
        }
        return feedbackStore.blockedAuthorIds(viewerUid);
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
                    LogMask.id(viewerUid), domain, deliveredItemCount, supportHitItemCount, e);
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

    private String crossDomainReason(Integer sourceDomain,
                                     Integer targetDomain,
                                     List<String> baseReasons,
                                     String fallbackReason) {
        if (fallbackReason != null && !fallbackReason.isBlank()) {
            return fallbackReason;
        }
        if (baseReasons != null && !baseReasons.isEmpty()) {
            return baseReasons.get(0);
        }
        if (sourceDomain == null) {
            return "来自" + domainName(targetDomain) + "频道的相关延展内容";
        }
        return "从" + domainName(sourceDomain) + "延伸到"
                + domainName(targetDomain) + "频道的相关内容";
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

    private static boolean isRestoreAction(String action) {
        if (action == null) {
            return false;
        }
        String normalized = action.trim().toUpperCase();
        return "RESTORE".equals(normalized) || "MORE_LIKE_THIS".equals(normalized);
    }

    private static boolean isLegacyHideAuthorAction(String action) {
        return action != null && "HIDE_AUTHOR".equalsIgnoreCase(action.trim());
    }

    private boolean hasKnownPublicNonAnonymousPost(Long authorUid) {
        try {
            PageResult<PostBriefDTO> page = postFacade.getPostsByAuthor(
                    authorUid, 0L, AUTHOR_CONTROL_PUBLIC_POST_SCAN_SIZE);
            if (page == null || page.getItems() == null) {
                return false;
            }
            return page.getItems().stream()
                    .anyMatch(post -> isBlockablePublicAuthor(post, null)
                            && Objects.equals(post.getAuthorId(), authorUid));
        } catch (RuntimeException e) {
            log.debug("author control target lookup rejected, authorUid={}", LogMask.id(authorUid), e);
            return false;
        }
    }

    private boolean isBlockablePublicAuthor(PostBriefDTO post, Long viewerUid) {
        return PublicContentFilter.isDistributablePost(post)
                && !Boolean.TRUE.equals(post.getAnonymous())
                && post.getAuthorId() != null
                && post.getAuthorId() > 0
                && !Objects.equals(post.getAuthorId(), viewerUid);
    }

    private static long safe(Long value) {
        return value == null ? 0L : value;
    }

    void setKafkaEnabled(boolean kafkaEnabled) {
        this.kafkaEnabled = kafkaEnabled;
    }

    void setFeedKafkaConsumerEnabled(boolean feedKafkaConsumerEnabled) {
        this.feedKafkaConsumerEnabled = feedKafkaConsumerEnabled;
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
                .filter(code -> !isHighRiskDomain(code))
                .toList();
    }

    private Integer inferCrossDomainSource(UserIntentDTO intent) {
        if (intent == null) {
            return null;
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
        return null;
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
        if (domain == null) {
            return null;
        }
        return switch (domain) {
            case Post.DOMAIN_TECH,
                 Post.DOMAIN_CAREER,
                 Post.DOMAIN_READING,
                 Post.DOMAIN_LIFESTYLE,
                 Post.DOMAIN_INVESTMENT -> domain;
            default -> null;
        };
    }

    private String domainName(Integer domain) {
        Integer effectiveDomain = effectiveDomain(domain);
        if (effectiveDomain == null) {
            return "未分类";
        }
        return switch (effectiveDomain) {
            case Post.DOMAIN_TECH -> "技术";
            case Post.DOMAIN_CAREER -> "职场";
            case Post.DOMAIN_READING -> "阅读";
            case Post.DOMAIN_LIFESTYLE -> "生活";
            case Post.DOMAIN_INVESTMENT -> "投资理财";
            default -> "未分类";
        };
    }

    private CrossDomainCursor parseCrossDomainCursor(String cursor) {
        if (cursor == null || cursor.isBlank() || "0".equals(cursor.trim())) {
            return CrossDomainCursor.empty();
        }
        String value = cursor.trim();
        if (value.chars().allMatch(Character::isDigit)) {
            try {
                long millis = Long.parseLong(value);
                return millis <= 0
                        ? CrossDomainCursor.empty()
                        : new CrossDomainCursor(LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(millis), java.time.ZoneOffset.UTC), 0L);
            } catch (NumberFormatException e) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "invalid cross-domain cursor");
            }
        }
        try {
            CursorUtils.TimeIdCursor decoded = CursorUtils.decodeTimeId(value, CROSS_DOMAIN_CURSOR_VERSION);
            return new CrossDomainCursor(decoded.time(), decoded.id());
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "invalid cross-domain cursor");
        }
    }

    private String crossDomainCursor(PostBriefDTO post) {
        if (post == null || post.getCreateTime() == null || post.getId() == null || post.getId() <= 0) {
            return null;
        }
        return CursorUtils.encodeTimeId(CROSS_DOMAIN_CURSOR_VERSION, post.getCreateTime(), post.getId());
    }

    private record CrossDomainCursor(LocalDateTime time, Long id) {
        private static CrossDomainCursor empty() {
            return new CrossDomainCursor(null, null);
        }

        private boolean present() {
            return time != null;
        }
    }

    private boolean matchesDomain(FeedItemVO item, Integer domain) {
        if (item == null || item.getPost() == null) {
            return false;
        }
        return Objects.equals(effectiveDomain(item.getPost().getDomain()), domain);
    }

    private CursorUtils.TimeIdCursor parseFollowingDbCursor(String cursor) {
        if (cursor == null || cursor.isBlank() || "0".equals(cursor.trim())) {
            return CursorUtils.TimeIdCursor.empty();
        }
        String value = cursor.trim();
        try {
            if (value.chars().allMatch(Character::isDigit)) {
                long raw = Long.parseLong(value);
                long millis = raw > 10_000_000_000_000L ? raw / 1_000_000L : raw;
                if (millis <= 0) {
                    throw new IllegalArgumentException("following cursor time must be positive");
                }
                return new CursorUtils.TimeIdCursor(
                        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), java.time.ZoneOffset.UTC),
                        Long.MAX_VALUE,
                        millis);
            }
            return CursorUtils.decodeTimeId(value, FOLLOWING_DB_CURSOR_VERSION);
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "无效的关注流游标");
        }
    }

    private String followingDbCursor(PostBriefDTO post) {
        if (post == null || post.getCreateTime() == null || post.getId() == null || post.getId() <= 0) {
            throw new IllegalStateException("following feed row is missing its keyset cursor");
        }
        return CursorUtils.encodeTimeId(FOLLOWING_DB_CURSOR_VERSION, post.getCreateTime(), post.getId());
    }

    private boolean isStrictlyOlder(PostBriefDTO post, LocalDateTime cursorTime, Long cursorId) {
        if (post == null || post.getCreateTime() == null || post.getId() == null) {
            return false;
        }
        if (cursorTime == null) {
            return true;
        }
        int timeOrder = post.getCreateTime().compareTo(cursorTime);
        return timeOrder < 0
                || (timeOrder == 0 && cursorId != null && post.getId() < cursorId);
    }

    private enum FeedSource {
        FOLLOWING("FOLLOWING", "关注流", "FOLLOWED_AUTHOR", "来自你关注的作者"),
        RECOMMEND("RECOMMEND", "推荐流", "RULE_MATCH", "根据公开内容特征推荐"),
        LATEST("LATEST", "最新发布", "RECENT_PUBLISHED", "按发布时间展示"),
        HOT("HOT", "社区热门", "COMMUNITY_HOT", "近期社区互动较多"),
        CROSS_DOMAIN("CROSS_DOMAIN", "跨领域发现", "CROSS_DOMAIN_MATCH", "来自相邻领域的公开内容");

        private final String type;
        private final String label;
        private final String reasonCode;
        private final String reasonText;

        FeedSource(String type, String label, String reasonCode, String reasonText) {
            this.type = type;
            this.label = label;
            this.reasonCode = reasonCode;
            this.reasonText = reasonText;
        }

        private String type() {
            return type;
        }

        private String label() {
            return label;
        }

        private String reasonCode() {
            return reasonCode;
        }

        private String reasonText() {
            return reasonText;
        }
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
