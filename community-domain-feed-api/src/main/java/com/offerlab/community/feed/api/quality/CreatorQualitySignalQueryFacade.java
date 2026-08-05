package com.offerlab.community.feed.api.quality;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.Collection;

/**
 * Read-only, aggregate query for Feed-derived quality-expectation signals.
 * Implementations must never expose individual feedback records or identities.
 */
public interface CreatorQualitySignalQueryFacade {

    CreatorQualitySignalQueryResult findActiveQualitySignals(
            Collection<Long> publicPostIds,
            LocalDateTime since,
            LocalDateTime now
    );

    /**
     * V32 revision-aware quality aggregate. The default preserves compatibility for
     * existing facade implementations until they opt into the Feed-backed query.
     */
    default RevisionAwareQualitySignalQueryResult findRevisionAwareActiveQualitySignals(
            Collection<QualitySignalWindow> windows,
            Instant baseWindowStart,
            Instant now
    ) {
        return RevisionAwareQualitySignalQueryResult.unavailable();
    }

    ChannelQualitySignalSummary findChannelQualitySignals(
            Collection<Integer> domainCodes,
            int minimumDistinctReaders,
            LocalDateTime since,
            LocalDateTime now
    );
}
