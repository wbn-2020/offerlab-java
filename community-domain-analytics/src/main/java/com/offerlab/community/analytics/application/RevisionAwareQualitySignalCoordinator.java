package com.offerlab.community.analytics.application;

import com.offerlab.community.feed.api.quality.CreatorQualitySignalQueryFacade;
import com.offerlab.community.feed.api.quality.QualitySignalWindow;
import com.offerlab.community.feed.api.quality.RevisionAwareQualitySignalQueryResult;
import com.offerlab.community.post.api.quality.PostContentRevisionQuery;
import com.offerlab.community.post.api.quality.PostContentRevisionQueryFacade;
import com.offerlab.community.post.api.quality.PostContentRevisionQueryResult;
import com.offerlab.community.post.api.quality.PostContentRevisionSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Shared, read-only coordination for revision-aware quality signals. It keeps
 * revision tokens and anonymous reader aggregates inside Analytics.
 */
@Service
@RequiredArgsConstructor
class RevisionAwareQualitySignalCoordinator {

    static final int PERIOD_DAYS = 30;
    static final int MINIMUM_DISTINCT_READERS = 5;
    private static final int QUERY_BATCH_SIZE = 100;

    private final PostContentRevisionQueryFacade postContentRevisionQuery;
    private final CreatorQualitySignalQueryFacade qualitySignalQuery;
    private final Clock analyticsClock;

    Window captureWindow() {
        Instant now = analyticsClock.instant();
        return new Window(now.minusSeconds(PERIOD_DAYS * 24L * 60L * 60L), now);
    }

    Resolution resolveAuthorOwned(Window window, Long authorUid, Collection<Long> postIds) {
        Map<Long, Long> authorIds = new LinkedHashMap<>();
        for (Long postId : normalizePostIds(postIds)) {
            authorIds.put(postId, authorUid);
        }
        return resolve(window, PostContentRevisionQuery.Scope.AUTHOR_OWNED, authorUid, List.of(), authorIds);
    }

    Resolution resolveAuthorizedChannels(Window window, Long operatorUid, Collection<Integer> authorizedChannelCodes,
                                         Map<Long, Long> authorIdsByPostId) {
        return resolve(window, PostContentRevisionQuery.Scope.AUTHORIZED_CHANNEL, operatorUid,
                normalizeChannelCodes(authorizedChannelCodes), normalizeAuthorIds(authorIdsByPostId));
    }

    private Resolution resolve(Window window, PostContentRevisionQuery.Scope scope, Long subjectUid,
                               List<Integer> authorizedChannelCodes, Map<Long, Long> authorIdsByPostId) {
        if (window == null || subjectUid == null || subjectUid <= 0) {
            return Resolution.unavailable();
        }
        if (authorIdsByPostId.isEmpty()) {
            return Resolution.available(Map.of());
        }

        Map<Long, Assessment> assessments = new LinkedHashMap<>();
        for (List<Long> postIds : batches(authorIdsByPostId.keySet())) {
            InitialWindows initial = loadInitialWindows(
                    window, scope, subjectUid, authorizedChannelCodes, postIds, authorIdsByPostId);
            if (!initial.available()) {
                return Resolution.unavailable();
            }
            if (initial.windows().isEmpty()) {
                continue;
            }

            RevisionAwareQualitySignalQueryResult feedResult = findSignals(
                    initial.windows().values(), window);
            if (feedResult == null || !feedResult.available()) {
                return Resolution.unavailable();
            }
            Map<Long, RevisionAwareQualitySignalQueryResult.RevisionAwareQualitySignalSnapshot> feedByPostId =
                    exactFeedSnapshots(feedResult.items(), initial.windows());
            if (feedByPostId == null) {
                return Resolution.unavailable();
            }

            FinalWindows current = reloadCurrentWindows(
                    window, scope, subjectUid, authorizedChannelCodes, initial.windows(), authorIdsByPostId);
            if (!current.available()) {
                return Resolution.unavailable();
            }
            for (Map.Entry<Long, RevisionAwareQualitySignalQueryResult.RevisionAwareQualitySignalSnapshot> entry
                    : feedByPostId.entrySet()) {
                QualitySignalWindow initialWindow = initial.windows().get(entry.getKey());
                QualitySignalWindow currentWindow = current.windows().get(entry.getKey());
                if (currentWindow == null || !Objects.equals(initialWindow.revisionToken(), currentWindow.revisionToken())) {
                    continue;
                }
                RevisionAwareQualitySignalQueryResult.RevisionAwareQualitySignalSnapshot signal = entry.getValue();
                assessments.put(entry.getKey(), new Assessment(
                        signal.currentRevisionDistinctReaderCount() >= MINIMUM_DISTINCT_READERS,
                        !initialWindow.hasEffectiveRevision()
                                ? false
                                : signal.priorRevisionDistinctReaderCount() >= MINIMUM_DISTINCT_READERS));
            }
        }
        return Resolution.available(assessments);
    }

    private InitialWindows loadInitialWindows(Window window, PostContentRevisionQuery.Scope scope, Long subjectUid,
                                              List<Integer> authorizedChannelCodes, List<Long> postIds,
                                              Map<Long, Long> authorIdsByPostId) {
        PostContentRevisionQueryResult result = queryPostWindows(
                window, scope, subjectUid, authorizedChannelCodes, postIds);
        if (result == null || !result.available()) {
            return InitialWindows.unavailable();
        }
        Map<Long, PostContentRevisionSnapshot> byPostId = exactPostSnapshots(result.items(), postIds);
        if (byPostId == null) {
            return InitialWindows.unavailable();
        }
        Map<Long, QualitySignalWindow> windows = new LinkedHashMap<>();
        for (Long postId : postIds) {
            PostContentRevisionSnapshot snapshot = byPostId.get(postId);
            if (snapshot.status() == PostContentRevisionSnapshot.Status.UNAVAILABLE) {
                return InitialWindows.unavailable();
            }
            if (snapshot.status() == PostContentRevisionSnapshot.Status.NOT_ELIGIBLE) {
                continue;
            }
            QualitySignalWindow qualityWindow = toQualityWindow(window, snapshot, authorIdsByPostId.get(postId));
            if (qualityWindow == null) {
                return InitialWindows.unavailable();
            }
            windows.put(postId, qualityWindow);
        }
        return InitialWindows.available(windows);
    }

    private FinalWindows reloadCurrentWindows(Window window, PostContentRevisionQuery.Scope scope, Long subjectUid,
                                              List<Integer> authorizedChannelCodes,
                                              Map<Long, QualitySignalWindow> initialWindows,
                                              Map<Long, Long> authorIdsByPostId) {
        List<Long> postIds = List.copyOf(initialWindows.keySet());
        PostContentRevisionQueryResult result = queryPostWindows(
                window, scope, subjectUid, authorizedChannelCodes, postIds);
        if (result == null || !result.available()) {
            return FinalWindows.unavailable();
        }
        Map<Long, PostContentRevisionSnapshot> byPostId = exactPostSnapshots(result.items(), postIds);
        if (byPostId == null) {
            return FinalWindows.unavailable();
        }
        Map<Long, QualitySignalWindow> windows = new LinkedHashMap<>();
        for (Long postId : postIds) {
            PostContentRevisionSnapshot snapshot = byPostId.get(postId);
            if (snapshot.status() == PostContentRevisionSnapshot.Status.UNAVAILABLE) {
                return FinalWindows.unavailable();
            }
            if (snapshot.status() == PostContentRevisionSnapshot.Status.NOT_ELIGIBLE) {
                continue;
            }
            QualitySignalWindow qualityWindow = toQualityWindow(window, snapshot, authorIdsByPostId.get(postId));
            if (qualityWindow == null) {
                return FinalWindows.unavailable();
            }
            windows.put(postId, qualityWindow);
        }
        return FinalWindows.available(windows);
    }

    private PostContentRevisionQueryResult queryPostWindows(Window window, PostContentRevisionQuery.Scope scope,
                                                            Long subjectUid, List<Integer> authorizedChannelCodes,
                                                            List<Long> postIds) {
        LocalDateTime baseWindowStart = LocalDateTime.ofInstant(window.baseWindowStart(), ZoneOffset.UTC);
        PostContentRevisionQuery query = scope == PostContentRevisionQuery.Scope.AUTHOR_OWNED
                ? PostContentRevisionQuery.authorOwned(subjectUid, postIds, baseWindowStart)
                : PostContentRevisionQuery.authorizedChannel(
                        subjectUid, postIds, baseWindowStart, authorizedChannelCodes);
        try {
            return postContentRevisionQuery.query(query);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private RevisionAwareQualitySignalQueryResult findSignals(Collection<QualitySignalWindow> windows, Window window) {
        try {
            return qualitySignalQuery.findRevisionAwareActiveQualitySignals(
                    windows, window.baseWindowStart(), window.now());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static QualitySignalWindow toQualityWindow(Window window, PostContentRevisionSnapshot snapshot,
                                                       Long authorUid) {
        if (authorUid == null || authorUid <= 0 || snapshot == null || snapshot.postId() == null
                || snapshot.postId() <= 0 || snapshot.revisionToken() == null || snapshot.revisionToken().isBlank()
                || snapshot.windowStart() == null) {
            return null;
        }
        boolean expectedRevision = snapshot.status() == PostContentRevisionSnapshot.Status.FOUND;
        if ((snapshot.status() != PostContentRevisionSnapshot.Status.FOUND
                && snapshot.status() != PostContentRevisionSnapshot.Status.NO_EFFECTIVE_REVISION)
                || snapshot.hasEffectiveRevision() != expectedRevision) {
            return null;
        }
        Instant effectiveWindowStart = snapshot.windowStart().toInstant(ZoneOffset.UTC);
        if (effectiveWindowStart.isBefore(window.baseWindowStart()) || effectiveWindowStart.isAfter(window.now())) {
            return null;
        }
        return new QualitySignalWindow(snapshot.postId(), authorUid, effectiveWindowStart,
                snapshot.hasEffectiveRevision(), snapshot.revisionToken());
    }

    private static Map<Long, PostContentRevisionSnapshot> exactPostSnapshots(
            List<PostContentRevisionSnapshot> snapshots, Collection<Long> expectedPostIds) {
        Map<Long, PostContentRevisionSnapshot> byPostId = new LinkedHashMap<>();
        Set<Long> expected = new LinkedHashSet<>(expectedPostIds);
        for (PostContentRevisionSnapshot snapshot : snapshots == null ? List.<PostContentRevisionSnapshot>of() : snapshots) {
            if (snapshot == null || snapshot.postId() == null || !expected.contains(snapshot.postId())
                    || byPostId.putIfAbsent(snapshot.postId(), snapshot) != null) {
                return null;
            }
        }
        return byPostId.keySet().equals(expected) ? byPostId : null;
    }

    private static Map<Long, RevisionAwareQualitySignalQueryResult.RevisionAwareQualitySignalSnapshot> exactFeedSnapshots(
            List<RevisionAwareQualitySignalQueryResult.RevisionAwareQualitySignalSnapshot> snapshots,
            Map<Long, QualitySignalWindow> expectedWindows) {
        Map<Long, RevisionAwareQualitySignalQueryResult.RevisionAwareQualitySignalSnapshot> byPostId =
                new LinkedHashMap<>();
        for (RevisionAwareQualitySignalQueryResult.RevisionAwareQualitySignalSnapshot snapshot
                : snapshots == null
                ? List.<RevisionAwareQualitySignalQueryResult.RevisionAwareQualitySignalSnapshot>of()
                : snapshots) {
            QualitySignalWindow expected = snapshot == null ? null : expectedWindows.get(snapshot.postId());
            if (expected == null || !Objects.equals(expected.revisionToken(), snapshot.revisionToken())
                    || byPostId.putIfAbsent(snapshot.postId(), snapshot) != null) {
                return null;
            }
        }
        return byPostId.keySet().equals(expectedWindows.keySet()) ? byPostId : null;
    }

    private static Map<Long, Long> normalizeAuthorIds(Map<Long, Long> authorIdsByPostId) {
        Map<Long, Long> normalized = new LinkedHashMap<>();
        if (authorIdsByPostId == null) {
            return normalized;
        }
        authorIdsByPostId.forEach((postId, authorUid) -> {
            if (postId != null && postId > 0 && authorUid != null && authorUid > 0) {
                normalized.putIfAbsent(postId, authorUid);
            }
        });
        return normalized;
    }

    private static List<Long> normalizePostIds(Collection<Long> postIds) {
        if (postIds == null) {
            return List.of();
        }
        return postIds.stream()
                .filter(Objects::nonNull)
                .filter(postId -> postId > 0)
                .distinct()
                .toList();
    }

    private static List<Integer> normalizeChannelCodes(Collection<Integer> channelCodes) {
        if (channelCodes == null) {
            return List.of();
        }
        return channelCodes.stream()
                .filter(Objects::nonNull)
                .filter(channelCode -> channelCode > 0)
                .distinct()
                .toList();
    }

    private static List<List<Long>> batches(Collection<Long> postIds) {
        List<Long> ids = List.copyOf(postIds);
        List<List<Long>> batches = new ArrayList<>();
        for (int start = 0; start < ids.size(); start += QUERY_BATCH_SIZE) {
            batches.add(ids.subList(start, Math.min(start + QUERY_BATCH_SIZE, ids.size())));
        }
        return batches;
    }

    record Window(Instant baseWindowStart, Instant now) {
    }

    record Assessment(boolean currentRevisionQualified, boolean updatedAwaitingAnonymousFeedback) {
    }

    record Resolution(Map<Long, Assessment> assessments, boolean available) {

        static Resolution available(Map<Long, Assessment> assessments) {
            return new Resolution(Map.copyOf(assessments), true);
        }

        static Resolution unavailable() {
            return new Resolution(Map.of(), false);
        }
    }

    private record InitialWindows(Map<Long, QualitySignalWindow> windows, boolean available) {

        static InitialWindows available(Map<Long, QualitySignalWindow> windows) {
            return new InitialWindows(Map.copyOf(windows), true);
        }

        static InitialWindows unavailable() {
            return new InitialWindows(Map.of(), false);
        }
    }

    private record FinalWindows(Map<Long, QualitySignalWindow> windows, boolean available) {

        static FinalWindows available(Map<Long, QualitySignalWindow> windows) {
            return new FinalWindows(Map.copyOf(windows), true);
        }

        static FinalWindows unavailable() {
            return new FinalWindows(Map.of(), false);
        }
    }
}
