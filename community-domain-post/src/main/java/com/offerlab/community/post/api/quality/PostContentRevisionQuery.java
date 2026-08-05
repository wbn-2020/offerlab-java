package com.offerlab.community.post.api.quality;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Authorization context and requested public post ids for an opaque revision-boundary query.
 * Channel codes supplied for {@link Scope#AUTHORIZED_CHANNEL} must already be authorized by
 * the caller's own policy boundary.
 */
public record PostContentRevisionQuery(
        Scope scope,
        Long subjectUid,
        List<Long> postIds,
        LocalDateTime windowStart,
        List<Integer> authorizedChannelCodes
) {
    public PostContentRevisionQuery {
        scope = Objects.requireNonNull(scope, "scope");
        windowStart = Objects.requireNonNull(windowStart, "windowStart");
        postIds = normalizePostIds(postIds);
        authorizedChannelCodes = normalizeChannelCodes(authorizedChannelCodes);
    }

    public static PostContentRevisionQuery authorOwned(Long authorUid, Collection<Long> postIds,
                                                        LocalDateTime windowStart) {
        return new PostContentRevisionQuery(Scope.AUTHOR_OWNED, authorUid,
                postIds == null ? List.of() : new ArrayList<>(postIds), windowStart, List.of());
    }

    public static PostContentRevisionQuery authorizedChannel(Long operatorUid, Collection<Long> postIds,
                                                              LocalDateTime windowStart,
                                                              Collection<Integer> authorizedChannelCodes) {
        return new PostContentRevisionQuery(Scope.AUTHORIZED_CHANNEL, operatorUid,
                postIds == null ? List.of() : new ArrayList<>(postIds), windowStart,
                authorizedChannelCodes == null ? List.of() : new ArrayList<>(authorizedChannelCodes));
    }

    private static List<Long> normalizePostIds(Collection<Long> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .filter(value -> value > 0)
                .distinct()
                .limit(500)
                .toList();
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

    public enum Scope {
        AUTHOR_OWNED,
        AUTHORIZED_CHANNEL
    }
}
