package com.offerlab.community.post.application;

import com.offerlab.community.post.api.quality.PostContentRevisionQuery;
import com.offerlab.community.post.api.quality.PostContentRevisionQueryFacade;
import com.offerlab.community.post.api.quality.PostContentRevisionQueryResult;
import com.offerlab.community.post.api.quality.PostContentRevisionSnapshot;
import com.offerlab.community.post.api.quality.PostPublicRevisionBoundaryPageQuery;
import com.offerlab.community.post.api.quality.PostPublicRevisionBoundaryPageResult;
import com.offerlab.community.post.api.quality.PostPublicRevisionBoundaryProjection;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostVersionHistoryMapper;
import com.offerlab.community.post.infrastructure.persistence.projection.PostContentRevisionQueryRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Read-only effective-revision projection. Schema or query failures are observable as
 * UNAVAILABLE so analytics never confuses them with a missing effective revision.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostContentRevisionQueryService implements PostContentRevisionQueryFacade {

    private static final int REQUIRED_QUALITY_SIGNAL_COLUMNS = 6;

    private final PostVersionHistoryMapper versionMapper;

    @Override
    public PostContentRevisionQueryResult query(PostContentRevisionQuery query) {
        if (query == null || query.postIds().isEmpty()) {
            return PostContentRevisionQueryResult.available(List.of());
        }
        try {
            if (versionMapper.qualitySignalSchemaColumnCount() != REQUIRED_QUALITY_SIGNAL_COLUMNS) {
                return unavailable(query);
            }
            Map<Long, PostContentRevisionQueryRow> rows = indexByPostId(
                    versionMapper.selectContentRevisionQueryRows(query.postIds()));
            return PostContentRevisionQueryResult.available(query.postIds().stream()
                    .map(postId -> toSnapshot(query, postId, rows.get(postId)))
                    .toList());
        } catch (RuntimeException e) {
            log.warn("effective content revision query unavailable, postCount={}", query.postIds().size(), e);
            return unavailable(query);
        }
    }

    @Override
    public PostPublicRevisionBoundaryPageResult queryPublicRevisionBoundaryPage(
            PostPublicRevisionBoundaryPageQuery query) {
        if (query == null) {
            return PostPublicRevisionBoundaryPageResult.unavailable();
        }
        if (!query.isAuthorizedChannelRequest()) {
            return PostPublicRevisionBoundaryPageResult.available(
                    List.of(), null, query.snapshotUpperBoundPostId());
        }
        try {
            if (versionMapper.qualitySignalSchemaColumnCount() != REQUIRED_QUALITY_SIGNAL_COLUMNS) {
                return PostPublicRevisionBoundaryPageResult.unavailable();
            }
            Long upperBound = query.snapshotUpperBoundPostId();
            if (upperBound == null) {
                upperBound = versionMapper.selectMaxPublicContentRevisionBoundaryPostId(query.domain());
            }
            if (upperBound == null || upperBound <= query.afterPostId()) {
                return PostPublicRevisionBoundaryPageResult.available(List.of(), null, upperBound);
            }
            Long snapshotUpperBound = upperBound;

            List<PostContentRevisionQueryRow> rows = versionMapper.selectPublicContentRevisionBoundaryRows(
                    query.domain(), query.afterPostId(), snapshotUpperBound, query.pageSize());
            List<PostPublicRevisionBoundaryProjection> items = toPublicBoundaryPageItems(
                    query, snapshotUpperBound, rows);
            Long lastPostId = items.isEmpty() ? null : items.get(items.size() - 1).postId();
            boolean hasMore = lastPostId != null
                    && versionMapper.existsPublicContentRevisionBoundaryAfter(
                    query.domain(), lastPostId, snapshotUpperBound);
            return PostPublicRevisionBoundaryPageResult.available(
                    items, hasMore ? lastPostId : null, snapshotUpperBound);
        } catch (RuntimeException e) {
            log.warn("public content revision boundary page unavailable, domain={}, afterPostId={}",
                    query.domain(), query.afterPostId(), e);
            return PostPublicRevisionBoundaryPageResult.unavailable();
        }
    }

    private PostContentRevisionQueryResult unavailable(PostContentRevisionQuery query) {
        return PostContentRevisionQueryResult.unavailable(query.postIds().stream()
                .map(postId -> new PostContentRevisionSnapshot(
                        postId,
                        PostContentRevisionSnapshot.Status.UNAVAILABLE,
                        query.windowStart(),
                        false,
                        null,
                        null,
                        null))
                .toList());
    }

    private PostContentRevisionSnapshot toSnapshot(PostContentRevisionQuery query, Long postId,
                                                   PostContentRevisionQueryRow row) {
        if (!isEligible(query, row)) {
            return noRevision(postId, query.windowStart(), PostContentRevisionSnapshot.Status.NOT_ELIGIBLE);
        }
        if (!isEffectiveWithinWindow(row, query.windowStart())) {
            return new PostContentRevisionSnapshot(
                    postId,
                    PostContentRevisionSnapshot.Status.NO_EFFECTIVE_REVISION,
                    query.windowStart(),
                    false,
                    noEffectiveRevisionToken(postId, row),
                    null,
                    null);
        }
        return new PostContentRevisionSnapshot(
                postId,
                PostContentRevisionSnapshot.Status.FOUND,
                query.windowStart(),
                true,
                row.getLatestEffectiveContentRevisionToken(),
                row.getEffectivePublishedPostVersion(),
                row.getLatestEffectiveContentRevisionAt());
    }

    private static PostContentRevisionSnapshot noRevision(Long postId, LocalDateTime windowStart,
                                                           PostContentRevisionSnapshot.Status status) {
        return new PostContentRevisionSnapshot(postId, status, windowStart, false, null, null, null);
    }

    private static String noEffectiveRevisionToken(Long postId, PostContentRevisionQueryRow row) {
        if (StringUtils.hasText(row.getLatestEffectiveContentRevisionToken())) {
            return row.getLatestEffectiveContentRevisionToken();
        }
        if (StringUtils.hasText(row.getQualitySignalRevisionToken())) {
            return row.getQualitySignalRevisionToken();
        }
        return "baseline-" + postId;
    }

    private static PostPublicRevisionBoundaryProjection toPublicBoundaryProjection(
            PostPublicRevisionBoundaryPageQuery query,
            Long snapshotUpperBoundPostId,
            PostContentRevisionQueryRow row) {
        if (!isPublicEligibleForDomain(row, query.domain())
                || row.getAuthorId() == null
                || row.getAuthorId() < 1
                || row.getPostId() <= query.afterPostId()
                || row.getPostId() > snapshotUpperBoundPostId) {
            throw new IllegalStateException("public revision boundary projection violated its query contract");
        }
        boolean effective = isEffectiveWithinWindow(row, query.windowStart());
        LocalDateTime projectionWindowStart = effective
                ? maxWindowStart(query.windowStart(), row.getLatestEffectiveContentRevisionAt())
                : query.windowStart();
        return new PostPublicRevisionBoundaryProjection(
                row.getPostId(),
                row.getDomain(),
                row.getAuthorId(),
                true,
                projectionWindowStart,
                effective ? row.getLatestEffectiveContentRevisionToken()
                        : noEffectiveRevisionToken(row.getPostId(), row),
                effective
                        ? PostPublicRevisionBoundaryProjection.Status.EFFECTIVE_REVISION
                        : PostPublicRevisionBoundaryProjection.Status.NO_EFFECTIVE_REVISION);
    }

    private static List<PostPublicRevisionBoundaryProjection> toPublicBoundaryPageItems(
            PostPublicRevisionBoundaryPageQuery query,
            Long snapshotUpperBoundPostId,
            List<PostContentRevisionQueryRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        if (rows.size() > query.pageSize()) {
            throw new IllegalStateException("public revision boundary page exceeded its requested limit");
        }
        long previousPostId = query.afterPostId();
        List<PostPublicRevisionBoundaryProjection> items = new java.util.ArrayList<>(rows.size());
        for (PostContentRevisionQueryRow row : rows) {
            if (row == null || row.getPostId() == null || row.getPostId() <= previousPostId) {
                throw new IllegalStateException("public revision boundary page keyset is not strictly ascending");
            }
            items.add(toPublicBoundaryProjection(query, snapshotUpperBoundPostId, row));
            previousPostId = row.getPostId();
        }
        return List.copyOf(items);
    }

    private static LocalDateTime maxWindowStart(LocalDateTime baseWindowStart, LocalDateTime effectiveRevisionAt) {
        return effectiveRevisionAt.isAfter(baseWindowStart) ? effectiveRevisionAt : baseWindowStart;
    }

    private static boolean isEligible(PostContentRevisionQuery query, PostContentRevisionQueryRow row) {
        if (!isPublicEligibleForDomain(row, null)) {
            return false;
        }
        if (query.scope() == PostContentRevisionQuery.Scope.AUTHOR_OWNED) {
            return query.subjectUid() != null && Objects.equals(query.subjectUid(), row.getAuthorId());
        }
        return query.subjectUid() != null
                && query.authorizedChannelCodes().contains(row.getDomain());
    }

    private static boolean isPublicEligibleForDomain(PostContentRevisionQueryRow row, Integer requiredDomain) {
        return row != null
                && row.getPostId() != null
                && row.getPostId() > 0
                && Objects.equals(row.getIsDeleted(), 0)
                && Objects.equals(row.getPostStatus(), Post.STATUS_PUBLISHED)
                && (row.getVisibility() == null || Objects.equals(row.getVisibility(), Post.VIS_PUBLIC))
                && Post.isCommunityContent(row.getContentEnvironment())
                && row.getDomain() != null
                && row.getDomain() > 0
                && !(Objects.equals(row.getDomain(), Post.DOMAIN_CAREER)
                && Boolean.TRUE.equals(row.getAnonymous()))
                && (requiredDomain == null || Objects.equals(requiredDomain, row.getDomain()));
    }

    private static boolean isEffectiveWithinWindow(PostContentRevisionQueryRow row, LocalDateTime windowStart) {
        return StringUtils.hasText(row.getLatestEffectiveContentRevisionToken())
                && Objects.equals(row.getLatestEffectiveContentRevisionToken(), row.getQualitySignalRevisionToken())
                && "EFFECTIVE".equals(row.getQualitySignalRevisionState())
                && row.getLatestEffectiveContentRevisionAt() != null
                && row.getQualitySignalEffectiveAt() != null
                && row.getEffectivePublishedPostVersion() != null
                && row.getEffectivePublishedPostVersion() > 0
                && !row.getLatestEffectiveContentRevisionAt().isBefore(windowStart);
    }

    private static Map<Long, PostContentRevisionQueryRow> indexByPostId(
            Collection<PostContentRevisionQueryRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<Long, PostContentRevisionQueryRow> indexed = new HashMap<>();
        for (PostContentRevisionQueryRow row : rows) {
            if (row != null && row.getPostId() != null && row.getPostId() > 0) {
                indexed.putIfAbsent(row.getPostId(), row);
            }
        }
        return indexed;
    }
}
