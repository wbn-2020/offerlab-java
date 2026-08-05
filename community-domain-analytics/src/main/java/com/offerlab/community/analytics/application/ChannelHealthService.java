package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.api.dto.ChannelHealthDTO;
import com.offerlab.community.analytics.infrastructure.persistence.mapper.GrowthInsightMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.feed.api.quality.CreatorQualitySignalQueryFacade;
import com.offerlab.community.feed.api.quality.QualitySignalWindow;
import com.offerlab.community.feed.api.quality.RevisionAwareQualitySignalQueryResult;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.post.application.DomainModeratorService;
import com.offerlab.community.post.api.quality.PostContentRevisionQuery;
import com.offerlab.community.post.api.quality.PostContentRevisionQueryFacade;
import com.offerlab.community.post.api.quality.PostContentRevisionQueryResult;
import com.offerlab.community.post.api.quality.PostContentRevisionSnapshot;
import com.offerlab.community.post.api.quality.PostPublicRevisionBoundaryPageQuery;
import com.offerlab.community.post.api.quality.PostPublicRevisionBoundaryPageResult;
import com.offerlab.community.post.api.quality.PostPublicRevisionBoundaryProjection;
import com.offerlab.community.post.domain.model.Post;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChannelHealthService {

    private static final int QUALITY_SIGNAL_PERIOD_DAYS = RevisionAwareQualitySignalCoordinator.PERIOD_DAYS;
    private static final int CHANNEL_PROJECTION_PAGE_SIZE = 100;
    private static final int MAX_CHANNEL_PROJECTION_CANDIDATES = 250;

    private final GrowthInsightMapper growthInsightMapper;
    private final RevisionAwareQualitySignalCoordinator qualitySignalCoordinator;
    private final PostContentRevisionQueryFacade postContentRevisionQuery;
    private final CreatorQualitySignalQueryFacade qualitySignalQuery;
    private final AdminPermissionService adminPermissionService;
    private final DomainModeratorService domainModeratorService;
    private final MigrationCheckService migrationCheckService;

    public List<ChannelHealthDTO> list(Integer domain, Long uid) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!migrationCheckService.trustedContentReady()
                || !migrationCheckService.trustedDistributionReady()
                || !migrationCheckService.stageTwoToFiveReady()) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                    "Channel health requires the trusted distribution migration");
        }
        boolean revisionBoundaryReady = migrationCheckService.creatorQualityProjectionReady();
        RevisionAwareQualitySignalCoordinator.Window qualityWindow = revisionBoundaryReady
                ? qualitySignalCoordinator.captureWindow()
                : null;
        if (isGlobalModerator(uid)) {
            return query(domain, uid, null, qualityWindow);
        }
        List<Integer> moderatedDomains = domainModeratorService.listModeratedDomains(uid);
        if (domain != null) {
            if (!moderatedDomains.contains(domain)) {
                throw new BizException(ErrorCode.FORBIDDEN);
            }
            return query(domain, uid, moderatedDomains, qualityWindow);
        }
        if (moderatedDomains.isEmpty()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return moderatedDomains.stream()
                .sorted()
                .flatMap(item -> query(item, uid, moderatedDomains, qualityWindow).stream())
                .toList();
    }

    private List<ChannelHealthDTO> query(Integer domain, Long uid, List<Integer> authorizedDomains,
                                         RevisionAwareQualitySignalCoordinator.Window qualityWindow) {
        List<Map<String, Object>> rows = growthInsightMapper.selectChannelHealth(domain, Post.TYPE_COMMUNITY_QUESTION);
        List<Integer> domains = rows.stream()
                .map(row -> integer(row.get("domain")))
                .filter(value -> value != null && value > 0)
                .distinct()
                .toList();
        List<Integer> postAuthorizedDomains = authorizedDomains == null ? domains : authorizedDomains;
        ChannelQualitySignals qualitySignals = collectQualitySignals(
                qualityWindow, uid, domains, postAuthorizedDomains);
        return rows.stream()
                .map(row -> toDto(row, qualitySignals))
                .toList();
    }

    private ChannelQualitySignals collectQualitySignals(RevisionAwareQualitySignalCoordinator.Window qualityWindow,
                                                        Long uid, List<Integer> domains,
                                                        List<Integer> authorizedDomains) {
        if (qualityWindow == null) {
            return ChannelQualitySignals.unavailable();
        }
        Map<Integer, Long> qualifiedPostCounts = new HashMap<>();
        int[] remainingCandidates = {MAX_CHANNEL_PROJECTION_CANDIDATES};
        for (Integer domain : domains) {
            ChannelQualitySignals domainSignals = collectDomainQualitySignals(
                    qualityWindow, uid, domain, authorizedDomains, remainingCandidates);
            if (!domainSignals.available()) {
                return ChannelQualitySignals.unavailable();
            }
            qualifiedPostCounts.put(domain, domainSignals.qualifiedPostCounts().getOrDefault(domain, 0L));
        }
        return ChannelQualitySignals.available(qualifiedPostCounts);
    }

    private ChannelQualitySignals collectDomainQualitySignals(RevisionAwareQualitySignalCoordinator.Window qualityWindow,
                                                              Long uid, Integer domain,
                                                              List<Integer> authorizedDomains,
                                                              int[] remainingCandidates) {
        if (domain == null || domain <= 0) {
            return ChannelQualitySignals.unavailable();
        }
        LocalDateTime baseWindowStart = LocalDateTime.ofInstant(qualityWindow.baseWindowStart(), ZoneOffset.UTC);
        Long cursor = 0L;
        Long snapshotUpperBound = null;
        long qualifiedPostCount = 0L;
        Set<Long> scannedPostIds = new HashSet<>();
        Set<Long> scannedCursors = new HashSet<>();

        while (true) {
            int pageSize = Math.min(CHANNEL_PROJECTION_PAGE_SIZE,
                    remainingCandidates[0]);
            if (pageSize <= 0) {
                return ChannelQualitySignals.unavailable();
            }
            PostPublicRevisionBoundaryPageResult page;
            try {
                page = postContentRevisionQuery.queryPublicRevisionBoundaryPage(
                        PostPublicRevisionBoundaryPageQuery.authorizedChannel(
                                uid, domain, authorizedDomains, baseWindowStart, cursor, snapshotUpperBound, pageSize));
            } catch (RuntimeException ignored) {
                return ChannelQualitySignals.unavailable();
            }
            if (page == null || !page.available()) {
                return ChannelQualitySignals.unavailable();
            }
            if (!validSnapshotUpperBound(page, cursor, snapshotUpperBound)) {
                return ChannelQualitySignals.unavailable();
            }
            snapshotUpperBound = page.snapshotUpperBoundPostId();
            List<PostPublicRevisionBoundaryProjection> projections = page.items() == null ? List.of() : page.items();
            if (!validProjectionPage(projections, domain, cursor, snapshotUpperBound, qualityWindow, scannedPostIds)) {
                return ChannelQualitySignals.unavailable();
            }
            remainingCandidates[0] -= projections.size();

            RevisionAwareQualitySignalQueryResult signals = querySignals(projections, qualityWindow);
            Map<Long, RevisionAwareQualitySignalQueryResult.RevisionAwareQualitySignalSnapshot> signalsByPostId =
                    exactSignals(projections, signals);
            if (signalsByPostId == null) {
                return ChannelQualitySignals.unavailable();
            }
            if (!projectionTokensStillCurrent(projections, qualityWindow, uid, authorizedDomains)) {
                return ChannelQualitySignals.unavailable();
            }
            qualifiedPostCount += signalsByPostId.values().stream()
                    .filter(signal -> signal.currentRevisionDistinctReaderCount()
                            >= RevisionAwareQualitySignalCoordinator.MINIMUM_DISTINCT_READERS)
                    .count();

            Long nextCursor = page.nextCursor();
            if (nextCursor == null) {
                if (projections.isEmpty() && snapshotUpperBound != null && snapshotUpperBound > cursor) {
                    return ChannelQualitySignals.unavailable();
                }
                return ChannelQualitySignals.available(Map.of(domain, qualifiedPostCount));
            }
            if (projections.isEmpty()
                    || nextCursor <= cursor
                    || !scannedCursors.add(nextCursor)
                    || snapshotUpperBound == null
                    || nextCursor > snapshotUpperBound
                    || remainingCandidates[0] <= 0) {
                return ChannelQualitySignals.unavailable();
            }
            cursor = nextCursor;
        }
    }

    private boolean projectionTokensStillCurrent(List<PostPublicRevisionBoundaryProjection> projections,
                                                 RevisionAwareQualitySignalCoordinator.Window qualityWindow,
                                                 Long uid, List<Integer> authorizedDomains) {
        PostContentRevisionQueryResult current;
        try {
            current = postContentRevisionQuery.query(PostContentRevisionQuery.authorizedChannel(
                    uid,
                    projections.stream().map(PostPublicRevisionBoundaryProjection::postId).toList(),
                    LocalDateTime.ofInstant(qualityWindow.baseWindowStart(), ZoneOffset.UTC),
                    authorizedDomains));
        } catch (RuntimeException ignored) {
            return false;
        }
        if (current == null || !current.available() || current.items().size() != projections.size()) {
            return false;
        }
        Map<Long, PostContentRevisionSnapshot> byPostId = new HashMap<>();
        for (PostContentRevisionSnapshot snapshot : current.items()) {
            if (snapshot == null || snapshot.postId() == null
                    || byPostId.putIfAbsent(snapshot.postId(), snapshot) != null) {
                return false;
            }
        }
        for (PostPublicRevisionBoundaryProjection projection : projections) {
            PostContentRevisionSnapshot snapshot = byPostId.get(projection.postId());
            if (snapshot == null
                    || !Objects.equals(projection.revisionToken(), snapshot.revisionToken())
                    || (projection.status() == PostPublicRevisionBoundaryProjection.Status.EFFECTIVE_REVISION)
                    != snapshot.hasEffectiveRevision()) {
                return false;
            }
        }
        return true;
    }

    private RevisionAwareQualitySignalQueryResult querySignals(
            List<PostPublicRevisionBoundaryProjection> projections,
            RevisionAwareQualitySignalCoordinator.Window qualityWindow) {
        if (projections.isEmpty()) {
            return RevisionAwareQualitySignalQueryResult.available(List.of());
        }
        List<QualitySignalWindow> windows = projections.stream()
                .map(projection -> new QualitySignalWindow(
                        projection.postId(),
                        projection.authorId(),
                        projection.windowStart().toInstant(ZoneOffset.UTC),
                        projection.status() == PostPublicRevisionBoundaryProjection.Status.EFFECTIVE_REVISION,
                        projection.revisionToken()))
                .toList();
        try {
            return qualitySignalQuery.findRevisionAwareActiveQualitySignals(
                    windows, qualityWindow.baseWindowStart(), qualityWindow.now());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean validSnapshotUpperBound(PostPublicRevisionBoundaryPageResult page,
                                                   Long cursor, Long expectedUpperBound) {
        Long actualUpperBound = page.snapshotUpperBoundPostId();
        List<PostPublicRevisionBoundaryProjection> items = page.items() == null
                ? List.of()
                : page.items();
        if (expectedUpperBound != null) {
            return Objects.equals(expectedUpperBound, actualUpperBound);
        }
        if (actualUpperBound == null) {
            return items.isEmpty() && page.nextCursor() == null;
        }
        return actualUpperBound > cursor;
    }

    private static boolean validProjectionPage(List<PostPublicRevisionBoundaryProjection> projections,
                                               Integer domain, Long cursor, Long snapshotUpperBound,
                                               RevisionAwareQualitySignalCoordinator.Window qualityWindow,
                                               Set<Long> scannedPostIds) {
        if (projections.size() > CHANNEL_PROJECTION_PAGE_SIZE) {
            return false;
        }
        long previousPostId = cursor;
        for (PostPublicRevisionBoundaryProjection projection : projections) {
            if (projection == null
                    || projection.postId() == null
                    || projection.postId() <= previousPostId
                    || projection.domain() == null
                    || !projection.domain().equals(domain)
                    || projection.authorId() == null
                    || projection.authorId() <= 0
                    || !projection.publicEligible()
                    || projection.windowStart() == null
                    || projection.windowStart().toInstant(ZoneOffset.UTC).isBefore(qualityWindow.baseWindowStart())
                    || projection.windowStart().toInstant(ZoneOffset.UTC).isAfter(qualityWindow.now())
                    || projection.revisionToken() == null
                    || projection.revisionToken().isBlank()
                    || snapshotUpperBound == null
                    || projection.postId() > snapshotUpperBound
                    || !scannedPostIds.add(projection.postId())) {
                return false;
            }
            previousPostId = projection.postId();
        }
        return true;
    }

    private static Map<Long, RevisionAwareQualitySignalQueryResult.RevisionAwareQualitySignalSnapshot> exactSignals(
            List<PostPublicRevisionBoundaryProjection> projections,
            RevisionAwareQualitySignalQueryResult signals) {
        if (signals == null || !signals.available()) {
            return null;
        }
        Map<Long, String> expectedTokens = new LinkedHashMap<>();
        for (PostPublicRevisionBoundaryProjection projection : projections) {
            if (expectedTokens.putIfAbsent(projection.postId(), projection.revisionToken()) != null) {
                return null;
            }
        }
        Map<Long, RevisionAwareQualitySignalQueryResult.RevisionAwareQualitySignalSnapshot> byPostId =
                new LinkedHashMap<>();
        for (RevisionAwareQualitySignalQueryResult.RevisionAwareQualitySignalSnapshot signal
                : signals.items() == null
                ? List.<RevisionAwareQualitySignalQueryResult.RevisionAwareQualitySignalSnapshot>of()
                : signals.items()) {
            String expectedToken = signal == null ? null : expectedTokens.get(signal.postId());
            if (expectedToken == null
                    || !Objects.equals(expectedToken, signal.revisionToken())
                    || byPostId.putIfAbsent(signal.postId(), signal) != null) {
                return null;
            }
        }
        return byPostId.keySet().equals(expectedTokens.keySet()) ? byPostId : null;
    }

    private boolean isGlobalModerator(Long uid) {
        return adminPermissionService.isAdmin(uid)
                || adminPermissionService.hasRole(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR)
                || adminPermissionService.isLocalOpenMode();
    }

    private ChannelHealthDTO toDto(Map<String, Object> row, ChannelQualitySignals qualitySignals) {
        Integer domain = integer(row.get("domain"));
        long publicPosts = nonNegative(row.get("publicPostCount"));
        long trustProfiles = nonNegative(row.get("trustProfileCount"));
        long freshness = nonNegative(row.get("freshnessAwaitingConfirmation"));
        long suggestions = nonNegative(row.get("pendingSuggestions"));
        long questions = nonNegative(row.get("unresolvedQuestions"));
        long needs = nonNegative(row.get("openContentNeeds"));
        boolean qualitySignalAvailable = qualitySignals != null && qualitySignals.available();
        long qualityReviewPostCount = qualitySignalAvailable
                ? nonNegative(qualitySignals.qualifiedPostCounts().get(domain))
                : 0L;
        int coverage = publicPosts == 0 ? 0 : (int) Math.min(100, trustProfiles * 100 / publicPosts);
        List<String> reasons = new ArrayList<>();
        if (publicPosts > 0 && coverage < 40) reasons.add("可信经验背景覆盖率低于 40%");
        if (freshness > 0) reasons.add("存在等待作者确认时效的内容");
        if (suggestions > 0) reasons.add("存在待处理的补充或纠错建议");
        if (questions > 0) reasons.add("存在尚未闭环的问题");
        if (needs > 0) reasons.add("存在待交付的公开内容需求");
        if (qualitySignalAvailable && qualityReviewPostCount > 0) {
            reasons.add("存在需要作者复核的匿名质量信号");
        }
        if (!qualitySignalAvailable) {
            reasons.add("匿名质量信号暂不可用");
        }
        return ChannelHealthDTO.builder()
                .domain(domain)
                .domainName(domainName(domain))
                .publicPostCount(publicPosts)
                .trustProfileCount(trustProfiles)
                .trustProfileCoveragePercent(coverage)
                .freshnessAwaitingConfirmation(freshness)
                .pendingSuggestions(suggestions)
                .unresolvedQuestions(questions)
                .openContentNeeds(needs)
                .qualityReviewPostCount(qualitySignalAvailable ? qualityReviewPostCount : null)
                .qualitySignalAvailable(qualitySignalAvailable)
                .qualitySignalPeriodDays(QUALITY_SIGNAL_PERIOD_DAYS)
                .healthStatus(!qualitySignalAvailable ? "DEGRADED" : (reasons.isEmpty() ? "STABLE" : "ATTENTION"))
                .attentionReasons(reasons)
                .build();
    }

    private static Integer integer(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null ? null : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static long nonNegative(Object value) {
        if (value instanceof Number number) return Math.max(0, number.longValue());
        try {
            return value == null ? 0 : Math.max(0, Long.parseLong(String.valueOf(value)));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String domainName(Integer domain) {
        return switch (domain == null ? 0 : domain) {
            case 1 -> "科技数码";
            case 2 -> "职场经验";
            case 3 -> "学习成长";
            case 4 -> "生活方式";
            case 5 -> "投资理财";
            default -> "未分类";
        };
    }

    private record ChannelQualitySignals(Map<Integer, Long> qualifiedPostCounts, boolean available) {

        static ChannelQualitySignals available(Map<Integer, Long> qualifiedPostCounts) {
            return new ChannelQualitySignals(Map.copyOf(qualifiedPostCounts), true);
        }

        static ChannelQualitySignals unavailable() {
            return new ChannelQualitySignals(Map.of(), false);
        }
    }
}
