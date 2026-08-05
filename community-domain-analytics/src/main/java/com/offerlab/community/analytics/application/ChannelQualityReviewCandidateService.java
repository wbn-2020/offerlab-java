package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.api.dto.ChannelQualityReviewCandidateDTO;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewCandidatePageDTO;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.feed.api.quality.CreatorQualitySignalQueryFacade;
import com.offerlab.community.feed.api.quality.QualitySignalWindow;
import com.offerlab.community.feed.api.quality.RevisionAwareQualitySignalQueryResult;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.post.api.ContentMaintenanceTaskReadFacade;
import com.offerlab.community.post.api.ContentMaintenanceTaskRevisionKey;
import com.offerlab.community.post.api.ContentMaintenanceTaskRevisionSummary;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.PublicContentFilter;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.quality.PostContentRevisionQuery;
import com.offerlab.community.post.api.quality.PostContentRevisionQueryFacade;
import com.offerlab.community.post.api.quality.PostContentRevisionQueryResult;
import com.offerlab.community.post.api.quality.PostContentRevisionSnapshot;
import com.offerlab.community.post.api.quality.PostPublicRevisionBoundaryPageQuery;
import com.offerlab.community.post.api.quality.PostPublicRevisionBoundaryPageResult;
import com.offerlab.community.post.api.quality.PostPublicRevisionBoundaryProjection;
import com.offerlab.community.post.application.DomainModeratorService;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewCandidateDispositionRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChannelQualityReviewCandidateService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 20;
    private static final int MAX_PROJECTION_SCAN = 100;
    private static final String SOURCE_TYPE = "CHANNEL_HEALTH";
    private static final String REASON_CODE = "REVISION_QUALITY_SIGNAL_READY";
    private static final String PRIORITY = "MEDIUM";
    private static final String DETAIL = "当前公开修订可进入质量复核流程";
    private static final Set<String> TASK_STATUSES = Set.of(
            "OPEN", "CLAIMED", "SUBMITTED", "COMPLETED", "CLOSED");
    private static final Set<String> MAINTENANCE_PHASES = Set.of(
            "OPEN", "IN_PROGRESS", "REWORK", "REVIEW_PENDING", "VERIFIED_DELIVERY", "CLOSED");
    private static final Set<String> TERMINAL_OUTCOMES = Set.of("VERIFIED_DELIVERY");

    private final RevisionAwareQualitySignalCoordinator qualitySignalCoordinator;
    private final PostContentRevisionQueryFacade postContentRevisionQuery;
    private final CreatorQualitySignalQueryFacade qualitySignalQuery;
    private final PostFacade postFacade;
    private final AdminPermissionService adminPermissionService;
    private final DomainModeratorService domainModeratorService;
    private final MigrationCheckService migrationCheckService;
    private final ContentMaintenanceTaskReadFacade maintenanceTaskReadFacade;
    private final ChannelQualityReviewCandidateDispositionService candidateDispositionService;

    public ChannelQualityReviewCandidatePageDTO list(Integer domain, String cursor, Integer size, Long uid) {
        requireOperator(uid);
        int requestedSize = requirePageSize(size);
        CandidateCursor requestedCursor = requireCursor(cursor);
        requireKnownDomain(domain);
        List<Integer> authorizedDomains = authorizedDomains(uid, domain);
        if (authorizedDomains == null) {
            return ChannelQualityReviewCandidatePageDTO.unavailable();
        }
        if (!dependenciesReady()) {
            return ChannelQualityReviewCandidatePageDTO.unavailable();
        }

        RevisionAwareQualitySignalCoordinator.Window qualityWindow;
        try {
            qualityWindow = qualitySignalCoordinator.captureWindow();
        } catch (RuntimeException ignored) {
            return ChannelQualityReviewCandidatePageDTO.unavailable();
        }
        if (qualityWindow == null) {
            return ChannelQualityReviewCandidatePageDTO.unavailable();
        }

        return collect(
                domain,
                uid,
                authorizedDomains,
                requestedCursor.afterPostId(),
                requestedCursor.snapshotUpperBoundPostId(),
                requestedSize,
                qualityWindow);
    }

    /**
     * Re-resolves candidate keys at dispatch time. The caller must treat any exception as a
     * whole-batch failure; no client-provided candidate metadata is trusted here.
     */
    public List<DispatchableCandidate> resolveReadyForDispatch(
            Integer domain,
            List<ContentMaintenanceTaskRevisionKey> keys,
            Long uid) {
        requireOperator(uid);
        requireKnownDomain(domain);
        List<ContentMaintenanceTaskRevisionKey> requestedKeys = requireDispatchKeys(keys);
        List<Integer> authorizedDomains = authorizedDomains(uid, domain);
        if (authorizedDomains == null || !dependenciesReady()) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }

        RevisionAwareQualitySignalCoordinator.Window qualityWindow;
        try {
            qualityWindow = qualitySignalCoordinator.captureWindow();
        } catch (RuntimeException ignored) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        if (qualityWindow == null) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }

        List<Long> postIds = requestedKeys.stream()
                .map(ContentMaintenanceTaskRevisionKey::sourcePostId)
                .toList();
        Map<Long, PostBriefDTO> posts = dispatchablePublicPosts(postIds, domain);
        Map<Long, PostContentRevisionSnapshot> snapshots = dispatchSnapshots(
                postIds, domain, uid, authorizedDomains, qualityWindow);
        Map<Long, RevisionAwareQualitySignalQueryResult.RevisionAwareQualitySignalSnapshot> signals =
                dispatchSignals(posts, snapshots, qualityWindow);
        Map<ContentMaintenanceTaskRevisionKey, ContentMaintenanceTaskRevisionSummary> summaries =
                taskSummariesForKeys(requestedKeys);
        Map<ContentMaintenanceTaskRevisionKey, ChannelQualityReviewCandidateDispositionRow> dispositions =
                dispatchDispositions(requestedKeys);
        if (posts == null || snapshots == null || signals == null
                || summaries == null || dispositions == null) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }

        List<DispatchableCandidate> resolved = new ArrayList<>(requestedKeys.size());
        for (ContentMaintenanceTaskRevisionKey key : requestedKeys) {
            PostBriefDTO post = posts.get(key.sourcePostId());
            PostContentRevisionSnapshot snapshot = snapshots.get(key.sourcePostId());
            RevisionAwareQualitySignalQueryResult.RevisionAwareQualitySignalSnapshot signal =
                    signals.get(key.sourcePostId());
            if (post == null
                    || snapshot == null
                    || signal == null
                    || snapshot.status() != PostContentRevisionSnapshot.Status.FOUND
                    || !snapshot.hasEffectiveRevision()
                    || snapshot.effectivePublishedPostVersion() == null
                    || snapshot.effectivePublishedPostVersion() <= 0
                    || key.sourceRefId() != snapshot.effectivePublishedPostVersion().longValue()
                    || !Objects.equals(snapshot.revisionToken(), signal.revisionToken())
                    || signal.currentRevisionDistinctReaderCount()
                    < RevisionAwareQualitySignalCoordinator.MINIMUM_DISTINCT_READERS) {
                throw new BizException(ErrorCode.DUPLICATE_OPERATION);
            }
            if (summaries.containsKey(key) || dispositions.containsKey(key)) {
                throw new BizException(ErrorCode.DUPLICATE_OPERATION);
            }
            resolved.add(new DispatchableCandidate(
                    key.sourcePostId(),
                    key.sourceRefId(),
                    post.getPostType(),
                    post.getTitle()));
        }
        return List.copyOf(resolved);
    }

    private Map<Long, PostBriefDTO> dispatchablePublicPosts(List<Long> postIds, int domain) {
        Map<Long, PostBriefDTO> queried;
        try {
            queried = postFacade.batchGetPosts(postIds, null, false);
        } catch (RuntimeException ignored) {
            return null;
        }
        if (queried == null || queried.size() != postIds.size()) {
            return null;
        }
        Map<Long, PostBriefDTO> result = new LinkedHashMap<>();
        for (Long postId : postIds) {
            PostBriefDTO post = queried.get(postId);
            if (post == null
                    || !Objects.equals(post.getId(), postId)
                    || !Objects.equals(post.getDomain(), domain)
                    || post.getAuthorId() == null
                    || post.getAuthorId() <= 0
                    || !Post.isSupportedType(post.getPostType())
                    || Boolean.TRUE.equals(post.getAnonymous())
                    || post.getTitle() == null
                    || post.getTitle().isBlank()
                    || !PublicContentFilter.isDistributablePost(post)
                    || result.putIfAbsent(postId, post) != null) {
                return null;
            }
        }
        return result;
    }

    private Map<Long, PostContentRevisionSnapshot> dispatchSnapshots(
            List<Long> postIds,
            int domain,
            Long uid,
            List<Integer> authorizedDomains,
            RevisionAwareQualitySignalCoordinator.Window qualityWindow) {
        PostContentRevisionQueryResult current;
        try {
            current = postContentRevisionQuery.query(PostContentRevisionQuery.authorizedChannel(
                    uid,
                    postIds,
                    LocalDateTime.ofInstant(qualityWindow.baseWindowStart(), ZoneOffset.UTC),
                    authorizedDomains));
        } catch (RuntimeException ignored) {
            return null;
        }
        if (current == null
                || !current.available()
                || current.items() == null
                || current.items().size() != postIds.size()) {
            return null;
        }
        Map<Long, PostContentRevisionSnapshot> result = new LinkedHashMap<>();
        for (PostContentRevisionSnapshot snapshot : current.items()) {
            if (snapshot == null
                    || snapshot.postId() == null
                    || !postIds.contains(snapshot.postId())
                    || snapshot.status() != PostContentRevisionSnapshot.Status.FOUND
                    || !snapshot.hasEffectiveRevision()
                    || snapshot.effectivePublishedPostVersion() == null
                    || snapshot.effectivePublishedPostVersion() <= 0
                    || snapshot.revisionToken() == null
                    || snapshot.revisionToken().isBlank()
                    || result.putIfAbsent(snapshot.postId(), snapshot) != null) {
                return null;
            }
        }
        return result.size() == postIds.size() ? result : null;
    }

    private Map<Long, RevisionAwareQualitySignalQueryResult.RevisionAwareQualitySignalSnapshot> dispatchSignals(
            Map<Long, PostBriefDTO> posts,
            Map<Long, PostContentRevisionSnapshot> snapshots,
            RevisionAwareQualitySignalCoordinator.Window qualityWindow) {
        if (posts == null || snapshots == null || posts.size() != snapshots.size()) {
            return null;
        }
        List<QualitySignalWindow> windows = new ArrayList<>(posts.size());
        for (Map.Entry<Long, PostBriefDTO> entry : posts.entrySet()) {
            PostContentRevisionSnapshot snapshot = snapshots.get(entry.getKey());
            if (snapshot == null) {
                return null;
            }
            windows.add(new QualitySignalWindow(
                    entry.getKey(),
                    entry.getValue().getAuthorId(),
                    qualityWindow.baseWindowStart(),
                    true,
                    snapshot.revisionToken()));
        }
        RevisionAwareQualitySignalQueryResult queried;
        try {
            queried = qualitySignalQuery.findRevisionAwareActiveQualitySignals(
                    windows, qualityWindow.baseWindowStart(), qualityWindow.now());
        } catch (RuntimeException ignored) {
            return null;
        }
        if (queried == null || !queried.available() || queried.items() == null
                || queried.items().size() != windows.size()) {
            return null;
        }
        Map<Long, RevisionAwareQualitySignalQueryResult.RevisionAwareQualitySignalSnapshot> result =
                new LinkedHashMap<>();
        for (RevisionAwareQualitySignalQueryResult.RevisionAwareQualitySignalSnapshot signal : queried.items()) {
            PostContentRevisionSnapshot snapshot = signal == null ? null : snapshots.get(signal.postId());
            if (signal == null
                    || snapshot == null
                    || !Objects.equals(snapshot.revisionToken(), signal.revisionToken())
                    || signal.currentRevisionDistinctReaderCount() < 0
                    || result.putIfAbsent(signal.postId(), signal) != null) {
                return null;
            }
        }
        return result.size() == windows.size() ? result : null;
    }

    private Map<ContentMaintenanceTaskRevisionKey, ContentMaintenanceTaskRevisionSummary> taskSummariesForKeys(
            List<ContentMaintenanceTaskRevisionKey> keys) {
        try {
            Map<ContentMaintenanceTaskRevisionKey, ContentMaintenanceTaskRevisionSummary> summaries =
                    maintenanceTaskReadFacade.findTaskRevisionSummariesBySourceRevision(SOURCE_TYPE, keys);
            return validTaskSummaries(keys, summaries) ? summaries : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Map<ContentMaintenanceTaskRevisionKey, ChannelQualityReviewCandidateDispositionRow> dispatchDispositions(
            List<ContentMaintenanceTaskRevisionKey> keys) {
        try {
            Map<ContentMaintenanceTaskRevisionKey, ChannelQualityReviewCandidateDispositionRow> dispositions =
                    candidateDispositionService.findActiveBySourceRevision(keys);
            if (dispositions == null) {
                return null;
            }
            for (ContentMaintenanceTaskRevisionKey key : dispositions.keySet()) {
                if (key == null || !keys.contains(key)) {
                    return null;
                }
            }
            return dispositions;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private ChannelQualityReviewCandidatePageDTO collect(Integer domain, Long uid, List<Integer> authorizedDomains,
                                                          long initialCursor, Long initialSnapshotUpperBound,
                                                          int requestedSize,
                                                          RevisionAwareQualitySignalCoordinator.Window qualityWindow) {
        List<ChannelQualityReviewCandidateDTO> candidates = new ArrayList<>(requestedSize);
        Set<Long> scannedPostIds = new HashSet<>();
        Set<Long> scannedCursors = new HashSet<>();
        long cursor = initialCursor;
        Long snapshotUpperBound = initialSnapshotUpperBound;
        int scanned = 0;
        int suppressedCount = 0;

        while (scanned < MAX_PROJECTION_SCAN) {
            int pageSize = Math.min(PostPublicRevisionBoundaryPageQuery.MAX_PAGE_SIZE, MAX_PROJECTION_SCAN - scanned);
            PostPublicRevisionBoundaryPageResult page = queryProjectionPage(
                    uid, domain, authorizedDomains, qualityWindow, cursor, snapshotUpperBound, pageSize);
            if (page == null || !page.available() || !validSnapshotUpperBound(page, cursor, snapshotUpperBound)) {
                return ChannelQualityReviewCandidatePageDTO.unavailable();
            }
            snapshotUpperBound = page.snapshotUpperBoundPostId();
            List<PostPublicRevisionBoundaryProjection> projections = page.items() == null ? List.of() : page.items();
            if (!validProjectionPage(projections, domain, cursor, snapshotUpperBound, qualityWindow, scannedPostIds)) {
                return ChannelQualityReviewCandidatePageDTO.unavailable();
            }
            if (page.nextCursor() != null
                    && (projections.isEmpty()
                    || !validNextCursor(page.nextCursor(), cursor, snapshotUpperBound, projections))) {
                return ChannelQualityReviewCandidatePageDTO.unavailable();
            }
            if (projections.isEmpty()) {
                return page.nextCursor() == null
                        ? ChannelQualityReviewCandidatePageDTO.available(null, suppressedCount, candidates)
                        : ChannelQualityReviewCandidatePageDTO.unavailable();
            }
            scanned += projections.size();

            Map<Long, RevisionAwareQualitySignalQueryResult.RevisionAwareQualitySignalSnapshot> signals =
                    exactSignals(projections, querySignals(projections, qualityWindow));
            if (signals == null) {
                return ChannelQualityReviewCandidatePageDTO.unavailable();
            }
            Map<Long, PostContentRevisionSnapshot> snapshots = currentSnapshots(
                    projections, qualityWindow, uid, authorizedDomains);
            if (snapshots == null) {
                return ChannelQualityReviewCandidatePageDTO.unavailable();
            }
            Map<Long, PostBriefDTO> publicPosts = publicPosts(projections);
            if (publicPosts == null) {
                return ChannelQualityReviewCandidatePageDTO.unavailable();
            }
            Map<ContentMaintenanceTaskRevisionKey, ContentMaintenanceTaskRevisionSummary> taskSummaries =
                    taskSummaries(projections, snapshots, signals);
            if (taskSummaries == null) {
                return ChannelQualityReviewCandidatePageDTO.unavailable();
            }
            Map<ContentMaintenanceTaskRevisionKey, ChannelQualityReviewCandidateDispositionRow> dispositions =
                    dispositions(projections, snapshots, signals);
            if (dispositions == null) {
                return ChannelQualityReviewCandidatePageDTO.unavailable();
            }

            for (int index = 0; index < projections.size(); index++) {
                PostPublicRevisionBoundaryProjection projection = projections.get(index);
                RevisionAwareQualitySignalQueryResult.RevisionAwareQualitySignalSnapshot signal =
                        signals.get(projection.postId());
                PostContentRevisionSnapshot snapshot = snapshots.get(projection.postId());
                PostBriefDTO post = publicPosts.get(projection.postId());
                if (post == null
                        || snapshot == null
                        || snapshot.status() != PostContentRevisionSnapshot.Status.FOUND
                        || !snapshot.hasEffectiveRevision()
                        || snapshot.effectivePublishedPostVersion() == null
                        || snapshot.effectivePublishedPostVersion() <= 0) {
                    suppressedCount++;
                    continue;
                }
                boolean signalQualified = projection.status()
                        == PostPublicRevisionBoundaryProjection.Status.EFFECTIVE_REVISION
                        && signal.currentRevisionDistinctReaderCount()
                        >= RevisionAwareQualitySignalCoordinator.MINIMUM_DISTINCT_READERS;
                if (!signalQualified) {
                    continue;
                }
                if (!Objects.equals(projection.revisionToken(), snapshot.revisionToken())) {
                    candidates.add(toCandidate(
                            domain, post, snapshot, "REVISION_STALE", null, null, null, null, null, false, null));
                } else {
                    ContentMaintenanceTaskRevisionKey key = new ContentMaintenanceTaskRevisionKey(
                            projection.postId(), snapshot.effectivePublishedPostVersion().longValue());
                    ContentMaintenanceTaskRevisionSummary taskSummary = taskSummaries.get(key);
                    String taskStatus = taskSummary == null ? null : taskSummary.status();
                    String maintenancePhase = taskSummary == null ? null : maintenancePhase(taskSummary);
                    String terminalOutcome = taskSummary == null ? null : terminalOutcome(taskSummary);
                    ChannelQualityReviewCandidateDispositionRow disposition = dispositions.get(key);
                    String lifecycleState;
                    String maintenanceStatus = taskStatus;
                    String dispositionReasonCode = null;
                    java.time.Instant snoozedUntil = null;
                    boolean actionable = taskStatus == null;
                    if (taskStatus != null) {
                        lifecycleState = "TASK_EXISTS";
                        actionable = false;
                    } else if (disposition != null) {
                        lifecycleState = disposition.getState();
                        dispositionReasonCode = disposition.getReasonCode();
                        snoozedUntil = disposition.getSnoozedUntil() == null
                                ? null
                                : disposition.getSnoozedUntil().toInstant(ZoneOffset.UTC);
                        actionable = false;
                    } else {
                        lifecycleState = "READY";
                    }
                    candidates.add(toCandidate(
                            domain,
                            post,
                            snapshot,
                            lifecycleState,
                            maintenanceStatus,
                            maintenancePhase,
                            terminalOutcome,
                            dispositionReasonCode,
                            snoozedUntil,
                            actionable,
                            snapshot.effectivePublishedPostVersion().longValue()));
                }
                if (candidates.size() == requestedSize) {
                    Long nextCursor = hasMoreInPageOrUpstream(page, projections, index)
                            ? projection.postId()
                            : null;
                    return ChannelQualityReviewCandidatePageDTO.available(
                            nextCursor == null ? null : encodeCursor(nextCursor, snapshotUpperBound),
                            suppressedCount,
                            candidates);
                }
            }

            Long nextCursor = page.nextCursor();
            if (nextCursor == null) {
                return ChannelQualityReviewCandidatePageDTO.available(null, suppressedCount, candidates);
            }
            if (!scannedCursors.add(nextCursor)) {
                return ChannelQualityReviewCandidatePageDTO.unavailable();
            }
            cursor = nextCursor;
        }

        return ChannelQualityReviewCandidatePageDTO.available(
                snapshotUpperBound != null && cursor < snapshotUpperBound
                        ? encodeCursor(cursor, snapshotUpperBound)
                        : null,
                suppressedCount,
                candidates);
    }

    private PostPublicRevisionBoundaryPageResult queryProjectionPage(
            Long uid, Integer domain, List<Integer> authorizedDomains,
            RevisionAwareQualitySignalCoordinator.Window qualityWindow, long cursor,
            Long snapshotUpperBound, int pageSize) {
        try {
            return postContentRevisionQuery.queryPublicRevisionBoundaryPage(
                    PostPublicRevisionBoundaryPageQuery.authorizedChannel(
                            uid,
                            domain,
                            authorizedDomains,
                            LocalDateTime.ofInstant(qualityWindow.baseWindowStart(), ZoneOffset.UTC),
                            cursor,
                            snapshotUpperBound,
                            pageSize));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private RevisionAwareQualitySignalQueryResult querySignals(
            List<PostPublicRevisionBoundaryProjection> projections,
            RevisionAwareQualitySignalCoordinator.Window qualityWindow) {
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

    private Map<Long, PostContentRevisionSnapshot> currentSnapshots(
            List<PostPublicRevisionBoundaryProjection> projections,
            RevisionAwareQualitySignalCoordinator.Window qualityWindow, Long uid, List<Integer> authorizedDomains) {
        PostContentRevisionQueryResult current;
        try {
            current = postContentRevisionQuery.query(PostContentRevisionQuery.authorizedChannel(
                    uid,
                    projections.stream().map(PostPublicRevisionBoundaryProjection::postId).toList(),
                    LocalDateTime.ofInstant(qualityWindow.baseWindowStart(), ZoneOffset.UTC),
                    authorizedDomains));
        } catch (RuntimeException ignored) {
            return null;
        }
        if (current == null
                || !current.available()
                || current.items() == null
                || current.items().size() != projections.size()) {
            return null;
        }
        Map<Long, PostContentRevisionSnapshot> byPostId = new HashMap<>();
        for (PostContentRevisionSnapshot snapshot : current.items()) {
            if (snapshot == null || snapshot.postId() == null
                    || byPostId.putIfAbsent(snapshot.postId(), snapshot) != null) {
                return null;
            }
        }
        for (PostPublicRevisionBoundaryProjection projection : projections) {
            PostContentRevisionSnapshot snapshot = byPostId.get(projection.postId());
            if (snapshot == null
                    || snapshot.status() == PostContentRevisionSnapshot.Status.UNAVAILABLE) {
                return null;
            }
            if (snapshot.hasEffectiveRevision()
                    && (snapshot.status() != PostContentRevisionSnapshot.Status.FOUND
                    || snapshot.effectivePublishedPostVersion() == null
                    || snapshot.effectivePublishedPostVersion() <= 0)) {
                return null;
            }
            if (!snapshot.hasEffectiveRevision()
                    && snapshot.status() != PostContentRevisionSnapshot.Status.NO_EFFECTIVE_REVISION) {
                return null;
            }
        }
        return byPostId;
    }

    private Map<Long, PostBriefDTO> publicPosts(List<PostPublicRevisionBoundaryProjection> projections) {
        List<Long> postIds = projections.stream().map(PostPublicRevisionBoundaryProjection::postId).toList();
        Map<Long, PostBriefDTO> posts;
        try {
            posts = postFacade.batchGetPosts(postIds, null, false);
        } catch (RuntimeException ignored) {
            return null;
        }
        if (posts == null) {
            return null;
        }
        Map<Long, PostBriefDTO> byPostId = new HashMap<>();
        for (PostBriefDTO post : posts.values()) {
            if (post == null || post.getId() == null || byPostId.putIfAbsent(post.getId(), post) != null) {
                return null;
            }
        }
        for (PostPublicRevisionBoundaryProjection projection : projections) {
            PostBriefDTO post = byPostId.get(projection.postId());
            if (post != null
                    && (!Objects.equals(post.getId(), projection.postId())
                    || !Objects.equals(post.getDomain(), projection.domain()))) {
                return null;
            }
            if (post != null
                    && (!Post.isSupportedType(post.getPostType())
                    || Boolean.TRUE.equals(post.getAnonymous())
                    || post.getTitle() == null
                    || post.getTitle().isBlank()
                    || !PublicContentFilter.isDistributablePost(post))) {
                byPostId.remove(projection.postId());
            }
        }
        return byPostId;
    }

    private Map<ContentMaintenanceTaskRevisionKey, ContentMaintenanceTaskRevisionSummary> taskSummaries(
            List<PostPublicRevisionBoundaryProjection> projections,
            Map<Long, PostContentRevisionSnapshot> snapshots,
            Map<Long, RevisionAwareQualitySignalQueryResult.RevisionAwareQualitySignalSnapshot> signals) {
        List<ContentMaintenanceTaskRevisionKey> keys = qualifiedKeys(projections, snapshots, signals);
        if (keys.isEmpty()) {
            return Map.of();
        }
        try {
            Map<ContentMaintenanceTaskRevisionKey, ContentMaintenanceTaskRevisionSummary> summaries =
                    maintenanceTaskReadFacade.findTaskRevisionSummariesBySourceRevision(SOURCE_TYPE, keys);
            return validTaskSummaries(keys, summaries) ? summaries : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Map<ContentMaintenanceTaskRevisionKey, ChannelQualityReviewCandidateDispositionRow> dispositions(
            List<PostPublicRevisionBoundaryProjection> projections,
            Map<Long, PostContentRevisionSnapshot> snapshots,
            Map<Long, RevisionAwareQualitySignalQueryResult.RevisionAwareQualitySignalSnapshot> signals) {
        List<ContentMaintenanceTaskRevisionKey> keys = qualifiedKeys(projections, snapshots, signals);
        if (keys.isEmpty()) {
            return Map.of();
        }
        try {
            return candidateDispositionService.findActiveBySourceRevision(keys);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static List<ContentMaintenanceTaskRevisionKey> qualifiedKeys(
            List<PostPublicRevisionBoundaryProjection> projections,
            Map<Long, PostContentRevisionSnapshot> snapshots,
            Map<Long, RevisionAwareQualitySignalQueryResult.RevisionAwareQualitySignalSnapshot> signals) {
        List<ContentMaintenanceTaskRevisionKey> keys = new ArrayList<>();
        for (PostPublicRevisionBoundaryProjection projection : projections) {
            PostContentRevisionSnapshot snapshot = snapshots.get(projection.postId());
            RevisionAwareQualitySignalQueryResult.RevisionAwareQualitySignalSnapshot signal =
                    signals.get(projection.postId());
            if (snapshot != null
                    && signal != null
                    && snapshot.status() == PostContentRevisionSnapshot.Status.FOUND
                    && snapshot.hasEffectiveRevision()
                    && snapshot.effectivePublishedPostVersion() != null
                    && snapshot.effectivePublishedPostVersion() > 0
                    && Objects.equals(projection.revisionToken(), snapshot.revisionToken())
                    && projection.status() == PostPublicRevisionBoundaryProjection.Status.EFFECTIVE_REVISION
                    && signal.currentRevisionDistinctReaderCount()
                    >= RevisionAwareQualitySignalCoordinator.MINIMUM_DISTINCT_READERS) {
                keys.add(new ContentMaintenanceTaskRevisionKey(
                        projection.postId(), snapshot.effectivePublishedPostVersion().longValue()));
            }
        }
        return keys;
    }

    private static boolean validTaskSummaries(
            List<ContentMaintenanceTaskRevisionKey> requestedKeys,
            Map<ContentMaintenanceTaskRevisionKey, ContentMaintenanceTaskRevisionSummary> summaries) {
        if (summaries == null) {
            return false;
        }
        for (Map.Entry<ContentMaintenanceTaskRevisionKey, ContentMaintenanceTaskRevisionSummary> entry
                : summaries.entrySet()) {
            if (entry.getKey() == null
                    || !requestedKeys.contains(entry.getKey())
                    || !validTaskSummary(entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static boolean validTaskSummary(ContentMaintenanceTaskRevisionSummary summary) {
        if (summary == null || !TASK_STATUSES.contains(summary.status())) {
            return false;
        }
        String phase = summary.maintenancePhase();
        if (phase != null
                && (!MAINTENANCE_PHASES.contains(phase)
                || !phaseMatchesStatus(summary.status(), phase))) {
            return false;
        }
        String outcome = summary.terminalOutcomeCode();
        return outcome == null
                || (TERMINAL_OUTCOMES.contains(outcome) && "COMPLETED".equals(summary.status()));
    }

    private static String maintenancePhase(ContentMaintenanceTaskRevisionSummary summary) {
        String phase = summary.maintenancePhase();
        return phase == null ? defaultMaintenancePhase(summary.status()) : phase;
    }

    private static String terminalOutcome(ContentMaintenanceTaskRevisionSummary summary) {
        if (summary.terminalOutcomeCode() != null) {
            return summary.terminalOutcomeCode();
        }
        return "COMPLETED".equals(summary.status()) ? "VERIFIED_DELIVERY" : null;
    }

    private static boolean phaseMatchesStatus(String status, String phase) {
        return switch (status) {
            case "OPEN" -> "OPEN".equals(phase);
            case "CLAIMED" -> "IN_PROGRESS".equals(phase) || "REWORK".equals(phase);
            case "SUBMITTED" -> "REVIEW_PENDING".equals(phase);
            case "COMPLETED" -> "VERIFIED_DELIVERY".equals(phase);
            case "CLOSED" -> "CLOSED".equals(phase);
            default -> false;
        };
    }

    private static String defaultMaintenancePhase(String status) {
        return switch (status) {
            case "OPEN" -> "OPEN";
            case "CLAIMED" -> "IN_PROGRESS";
            case "SUBMITTED" -> "REVIEW_PENDING";
            case "COMPLETED" -> "VERIFIED_DELIVERY";
            case "CLOSED" -> "CLOSED";
            default -> throw new IllegalStateException("unsupported maintenance task status");
        };
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

    private static boolean validSnapshotUpperBound(PostPublicRevisionBoundaryPageResult page,
                                                   long cursor, Long expectedUpperBound) {
        Long actualUpperBound = page.snapshotUpperBoundPostId();
        List<PostPublicRevisionBoundaryProjection> items = page.items() == null ? List.of() : page.items();
        if (expectedUpperBound != null) {
            return Objects.equals(expectedUpperBound, actualUpperBound);
        }
        if (actualUpperBound == null) {
            return items.isEmpty() && page.nextCursor() == null;
        }
        return actualUpperBound > cursor;
    }

    private static boolean validProjectionPage(List<PostPublicRevisionBoundaryProjection> projections,
                                               Integer domain, long cursor, Long snapshotUpperBound,
                                               RevisionAwareQualitySignalCoordinator.Window qualityWindow,
                                               Set<Long> scannedPostIds) {
        if (projections.size() > PostPublicRevisionBoundaryPageQuery.MAX_PAGE_SIZE) {
            return false;
        }
        long previousPostId = cursor;
        for (PostPublicRevisionBoundaryProjection projection : projections) {
            if (projection == null
                    || projection.postId() == null
                    || projection.postId() <= previousPostId
                    || !Objects.equals(projection.domain(), domain)
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

    private static boolean validNextCursor(Long nextCursor, long cursor, Long snapshotUpperBound,
                                           List<PostPublicRevisionBoundaryProjection> projections) {
        Long lastPostId = projections.get(projections.size() - 1).postId();
        return nextCursor != null
                && nextCursor.equals(lastPostId)
                && nextCursor > cursor
                && snapshotUpperBound != null
                && nextCursor <= snapshotUpperBound;
    }

    private static boolean hasMoreInPageOrUpstream(PostPublicRevisionBoundaryPageResult page,
                                                   List<PostPublicRevisionBoundaryProjection> projections,
                                                   int index) {
        return index + 1 < projections.size() || page.nextCursor() != null;
    }

    private static ChannelQualityReviewCandidateDTO toCandidate(
            Integer domain,
            PostBriefDTO post,
            PostContentRevisionSnapshot snapshot,
            String lifecycleState,
            String maintenanceStatus,
            String maintenancePhase,
            String terminalOutcome,
            String dispositionReasonCode,
            java.time.Instant snoozedUntil,
            boolean actionable,
            Long sourceRefId) {
        return ChannelQualityReviewCandidateDTO.builder()
                .domain(domain)
                .sourceType(SOURCE_TYPE)
                .sourcePostId(post.getId())
                .sourceRefId(sourceRefId)
                .sourcePostType(post.getPostType())
                .title(post.getTitle())
                .detail(DETAIL)
                .postHref("/post/" + post.getId())
                .reasonCode(REASON_CODE)
                .priority(PRIORITY)
                .lifecycleState(lifecycleState)
                .maintenanceStatus(maintenanceStatus)
                .maintenancePhase(maintenancePhase)
                .terminalOutcome(terminalOutcome)
                .dispositionReasonCode(dispositionReasonCode)
                .snoozedUntil(snoozedUntil)
                .actionable(actionable)
                .build();
    }

    private List<Integer> authorizedDomains(Long uid, Integer domain) {
        try {
            if (isGlobalModerator(uid)) {
                return List.of(domain);
            }
            if (!domainModeratorService.canModerateDomain(uid, domain)) {
                throw new BizException(ErrorCode.FORBIDDEN);
            }
            return List.of(domain);
        } catch (BizException e) {
            throw e;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean dependenciesReady() {
        try {
            return migrationCheckService.trustedContentReady()
                    && migrationCheckService.trustedDistributionReady()
                    && migrationCheckService.stageTwoToFiveReady()
                    && migrationCheckService.creatorQualityProjectionReady();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean isGlobalModerator(Long uid) {
        return adminPermissionService.isAdmin(uid)
                || adminPermissionService.hasRole(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR)
                || adminPermissionService.isLocalOpenMode();
    }

    private static void requireOperator(Long uid) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private static void requireKnownDomain(Integer domain) {
        if (domain == null || domain < Post.DOMAIN_TECH || domain > Post.DOMAIN_INVESTMENT) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private static int requirePageSize(Integer size) {
        int value = size == null ? DEFAULT_PAGE_SIZE : size;
        if (value < 1 || value > MAX_PAGE_SIZE) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return value;
    }

    private static List<ContentMaintenanceTaskRevisionKey> requireDispatchKeys(
            List<ContentMaintenanceTaskRevisionKey> keys) {
        if (keys == null || keys.isEmpty() || keys.size() > MAX_PAGE_SIZE) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        LinkedHashSet<ContentMaintenanceTaskRevisionKey> unique = new LinkedHashSet<>();
        for (ContentMaintenanceTaskRevisionKey key : keys) {
            if (key == null || !unique.add(key)) {
                throw new BizException(ErrorCode.PARAM_ERROR);
            }
        }
        return List.copyOf(unique);
    }

    private static CandidateCursor requireCursor(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return new CandidateCursor(0L, null);
        }
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(cursor.trim()), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 3 || !"chqc1".equals(parts[0])) {
                throw new IllegalArgumentException("unsupported cursor");
            }
            long afterPostId = Long.parseLong(parts[1]);
            long snapshotUpperBoundPostId = Long.parseLong(parts[2]);
            if (afterPostId <= 0
                    || snapshotUpperBoundPostId <= afterPostId) {
                throw new IllegalArgumentException("invalid cursor bounds");
            }
            return new CandidateCursor(afterPostId, snapshotUpperBoundPostId);
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private static String encodeCursor(long afterPostId, Long snapshotUpperBoundPostId) {
        if (afterPostId <= 0
                || snapshotUpperBoundPostId == null
                || snapshotUpperBoundPostId <= afterPostId) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        String raw = "chqc1|" + afterPostId + "|" + snapshotUpperBoundPostId;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public record DispatchableCandidate(
            Long sourcePostId,
            Long sourceRefId,
            Integer sourcePostType,
            String title) {
    }

    private record CandidateCursor(long afterPostId, Long snapshotUpperBoundPostId) {
    }
}
