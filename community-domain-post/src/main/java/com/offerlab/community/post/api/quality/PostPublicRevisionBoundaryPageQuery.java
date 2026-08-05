package com.offerlab.community.post.api.quality;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Pre-authorized channel projection request. The cursor is an ascending post-id keyset
 * and the upper bound must be retained by the caller for a stable multi-page scan.
 */
public record PostPublicRevisionBoundaryPageQuery(
        Long subjectUid,
        Integer domain,
        List<Integer> authorizedChannelCodes,
        LocalDateTime windowStart,
        Long afterPostId,
        Long snapshotUpperBoundPostId,
        int pageSize
) {
    public static final int MAX_PAGE_SIZE = 100;

    public PostPublicRevisionBoundaryPageQuery {
        authorizedChannelCodes = normalizeChannelCodes(authorizedChannelCodes);
        windowStart = Objects.requireNonNull(windowStart, "windowStart");
        afterPostId = positiveOrZero(afterPostId);
        snapshotUpperBoundPostId = positiveOrNull(snapshotUpperBoundPostId);
        pageSize = Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));
    }

    public static PostPublicRevisionBoundaryPageQuery authorizedChannel(
            Long operatorUid,
            Integer domain,
            Collection<Integer> authorizedChannelCodes,
            LocalDateTime windowStart,
            Long afterPostId,
            Long snapshotUpperBoundPostId,
            int pageSize) {
        return new PostPublicRevisionBoundaryPageQuery(
                operatorUid,
                domain,
                authorizedChannelCodes == null ? List.of() : new ArrayList<>(authorizedChannelCodes),
                windowStart,
                afterPostId,
                snapshotUpperBoundPostId,
                pageSize);
    }

    public boolean isAuthorizedChannelRequest() {
        return subjectUid != null
                && subjectUid > 0
                && domain != null
                && domain > 0
                && authorizedChannelCodes.contains(domain)
                && (snapshotUpperBoundPostId == null || snapshotUpperBoundPostId > afterPostId);
    }

    private static Long positiveOrZero(Long value) {
        return value == null || value < 1 ? 0L : value;
    }

    private static Long positiveOrNull(Long value) {
        return value == null || value < 1 ? null : value;
    }

    private static List<Integer> normalizeChannelCodes(Collection<Integer> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .filter(value -> value > 0)
                .distinct()
                .toList();
    }
}
