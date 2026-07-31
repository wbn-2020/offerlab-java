package com.offerlab.community.analytics.collaboration.application;

import com.offerlab.community.analytics.collaboration.api.CollaborationFunnelDTO;
import com.offerlab.community.analytics.collaboration.api.PublicContributionFactDTO;
import com.offerlab.community.analytics.collaboration.api.PublicContributionProfileDTO;
import com.offerlab.community.analytics.collaboration.infrastructure.persistence.CollaborationAnalyticsRows;
import com.offerlab.community.analytics.collaboration.infrastructure.persistence.mapper.CollaborationAnalyticsMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.post.application.DomainModeratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CollaborationAnalyticsService {

    private static final int MAX_FACTS = 200;
    private static final int MAX_DAYS = 90;
    private static final int STALE_AFTER_DAYS = 14;
    private static final String TIMEZONE = "UTC";

    private final CollaborationAnalyticsMapper mapper;
    private final AdminPermissionService adminPermissionService;
    private final DomainModeratorService domainModeratorService;

    public PublicContributionProfileDTO publicContributions(Long targetUid, int requestedLimit) {
        requirePositive(targetUid);
        requireSchema();

        int limit = Math.max(1, Math.min(requestedLimit <= 0 ? MAX_FACTS : requestedLimit, MAX_FACTS));
        List<CollaborationAnalyticsRows.ContributionFactRow> rows =
                mapper.selectPublicContributionFacts(targetUid, limit + 1);
        boolean truncated = rows.size() > limit;
        List<PublicContributionFactDTO> facts = rows.stream()
                .limit(limit)
                .map(CollaborationAnalyticsService::toFact)
                .toList();
        return PublicContributionProfileDTO.builder()
                .uid(targetUid)
                .factCount(facts.size())
                .truncated(truncated)
                .generatedAt(LocalDateTime.now(ZoneOffset.UTC))
                .facts(facts)
                .build();
    }

    public CollaborationFunnelDTO needFunnel(Long operatorUid, int requestedDays, Integer domain) {
        requirePositive(operatorUid);
        validateDomain(domain);
        requireOperationsAccess(operatorUid, domain);
        requireSchema();

        int days = Math.max(1, Math.min(requestedDays <= 0 ? 30 : requestedDays, MAX_DAYS));
        LocalDateTime windowEnd = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime windowStart = windowEnd.minusDays(days);
        LocalDateTime stalledBefore = windowEnd.minusDays(STALE_AFTER_DAYS);
        CollaborationAnalyticsRows.FunnelRow row =
                mapper.selectNeedFunnel(windowStart, windowEnd, stalledBefore, domain);
        return toFunnel(row, domain, windowStart, windowEnd, days);
    }

    private void requireOperationsAccess(Long uid, Integer domain) {
        boolean globalAccess = adminPermissionService.isAdmin(uid)
                || adminPermissionService.hasRole(uid, AdminPermissionService.ROLE_OPS)
                || adminPermissionService.hasRole(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR)
                || adminPermissionService.isLocalOpenMode();
        if (globalAccess) {
            return;
        }
        if (domain != null && domainModeratorService.canModerateDomain(uid, domain)) {
            return;
        }
        throw new BizException(ErrorCode.FORBIDDEN);
    }

    private void requireSchema() {
        try {
            if (mapper.schemaReady() >= 14) {
                return;
            }
        } catch (RuntimeException ignored) {
            // Keep schema failures on the dependency error contract.
        }
        throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                "Collaboration analytics tables are not ready");
    }

    private static PublicContributionFactDTO toFact(
            CollaborationAnalyticsRows.ContributionFactRow row) {
        return PublicContributionFactDTO.builder()
                .factType(row.getFactType())
                .sourceId(row.getSourceId())
                .referenceType(row.getReferenceType())
                .referenceId(row.getReferenceId())
                .domain(row.getDomain())
                .occurredAt(row.getOccurredAt())
                .build();
    }

    private static CollaborationFunnelDTO toFunnel(
            CollaborationAnalyticsRows.FunnelRow row,
            Integer domain,
            LocalDateTime windowStart,
            LocalDateTime windowEnd,
            int days) {
        CollaborationAnalyticsRows.FunnelRow safe = row == null
                ? new CollaborationAnalyticsRows.FunnelRow()
                : row;
        long created = nonNegative(safe.getCreatedNeedCount());
        long claimed = nonNegative(safe.getClaimedNeedCount());
        long submitted = nonNegative(safe.getSubmittedNeedCount());
        long accepted = nonNegative(safe.getAcceptedNeedCount());
        long rejected = nonNegative(safe.getRejectedNeedCount());
        long resubmitted = nonNegative(safe.getResubmittedNeedCount());
        long released = nonNegative(safe.getReleasedNeedCount());
        long reclaimed = nonNegative(safe.getReclaimedNeedCount());
        long activeClaimed = nonNegative(safe.getActiveClaimedNeedCount());
        long stalled = nonNegative(safe.getStalledNeedCount());
        long followed = nonNegative(safe.getFollowedNeedCount());
        long followedToClaimed = nonNegative(safe.getFollowedToClaimedNeedCount());
        long maintenanceTasks = nonNegative(safe.getMaintenanceTaskCount());
        long completedMaintenanceTasks = nonNegative(safe.getCompletedMaintenanceTaskCount());

        return CollaborationFunnelDTO.builder()
                .domain(domain)
                .windowStart(windowStart)
                .windowEnd(windowEnd)
                .timezone(TIMEZONE)
                .deduplicationPolicy(
                        "Lifecycle stages use distinct need_id in one created-need cohort; "
                                + "accepted facts use factType+sourceId; follow metrics use need_id+uid")
                .emptyDenominatorPolicy("0 when denominator is empty")
                .dataFreshness("MySQL read-side snapshot; no asynchronous projection")
                .degraded(false)
                .degradationReasons(List.of())
                .createdNeedCount(created)
                .claimedNeedCount(claimed)
                .submittedNeedCount(submitted)
                .acceptedNeedCount(accepted)
                .rejectedNeedCount(rejected)
                .resubmittedNeedCount(resubmitted)
                .releasedNeedCount(released)
                .reclaimedNeedCount(reclaimed)
                .activeClaimedNeedCount(activeClaimed)
                .stalledNeedCount(stalled)
                .followedNeedCount(followed)
                .followedToClaimedNeedCount(followedToClaimed)
                .maintenanceTaskCount(maintenanceTasks)
                .completedMaintenanceTaskCount(completedMaintenanceTasks)
                .claimRate(rate(claimed, created))
                .submitRate(rate(submitted, claimed))
                .acceptanceRate(rate(accepted, submitted))
                .rejectionResubmissionRate(rate(resubmitted, rejected))
                .releaseRate(rate(released, claimed))
                .reclaimRate(rate(reclaimed, released))
                .stalledRate(rate(stalled, activeClaimed))
                .followedToClaimedRate(rate(followedToClaimed, followed))
                .maintenanceCompletionRate(rate(completedMaintenanceTasks, maintenanceTasks))
                .averageCreateToClaimSeconds(nonNegative(safe.getAverageCreateToClaimSeconds()))
                .averageClaimToSubmitSeconds(nonNegative(safe.getAverageClaimToSubmitSeconds()))
                .averageSubmitToAcceptSeconds(nonNegative(safe.getAverageSubmitToAcceptSeconds()))
                .build();
    }

    private static BigDecimal rate(long numerator, long denominator) {
        if (denominator <= 0 || numerator <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    private static long nonNegative(Number value) {
        return value == null ? 0L : Math.max(0L, value.longValue());
    }

    private static void validateDomain(Integer domain) {
        if (domain != null && (domain < 1 || domain > 5)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private static void requirePositive(Long uid) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }
}
