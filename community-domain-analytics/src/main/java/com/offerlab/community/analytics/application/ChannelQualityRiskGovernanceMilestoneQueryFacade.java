package com.offerlab.community.analytics.application;

import java.time.Instant;
import java.util.List;

/**
 * V42-only stable fact boundary. Downstream versions must not query V41 tables directly.
 */
public interface ChannelQualityRiskGovernanceMilestoneQueryFacade {

    List<V41GovernanceFact> listCaseFacts(Long caseId);

    record V41GovernanceFact(
            Long sourceFactId,
            Long caseId,
            Integer domain,
            Integer caseVersion,
            String factType,
            Instant occurredAt,
            Long ownerUid,
            String responsibilityScope,
            Integer responsibilityEpoch,
            String caseStatusAfter,
            String requiredAction,
            Long retrospectiveId,
            String contractVersion) {
    }
}
