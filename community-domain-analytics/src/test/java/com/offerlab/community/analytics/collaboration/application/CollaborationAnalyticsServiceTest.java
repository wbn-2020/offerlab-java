package com.offerlab.community.analytics.collaboration.application;

import com.offerlab.community.analytics.collaboration.api.CollaborationFunnelDTO;
import com.offerlab.community.analytics.collaboration.api.PublicContributionProfileDTO;
import com.offerlab.community.analytics.collaboration.infrastructure.persistence.CollaborationAnalyticsRows;
import com.offerlab.community.analytics.collaboration.infrastructure.persistence.mapper.CollaborationAnalyticsMapper;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.post.application.DomainModeratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollaborationAnalyticsServiceTest {

    @Mock
    private CollaborationAnalyticsMapper mapper;
    @Mock
    private AdminPermissionService adminPermissionService;
    @Mock
    private DomainModeratorService domainModeratorService;

    private CollaborationAnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new CollaborationAnalyticsService(
                mapper, adminPermissionService, domainModeratorService);
    }

    @Test
    void publicContributionsArePublicFactsAndBounded() {
        when(mapper.schemaReady()).thenReturn(14);
        CollaborationAnalyticsRows.ContributionFactRow first =
                fact("NEED_ACCEPTED", 101L);
        CollaborationAnalyticsRows.ContributionFactRow second =
                fact("SERIES_SUBMISSION_ACCEPTED", 202L);
        CollaborationAnalyticsRows.ContributionFactRow extra =
                fact("ACTIVITY_SUBMISSION_ACCEPTED", 303L);
        when(mapper.selectPublicContributionFacts(8L, 3))
                .thenReturn(List.of(first, second, extra));

        PublicContributionProfileDTO profile = service.publicContributions(8L, 2);

        assertEquals(8L, profile.getUid());
        assertEquals(2, profile.getFactCount());
        assertTrue(profile.getTruncated());
        assertEquals("NEED_ACCEPTED", profile.getFacts().get(0).getFactType());
    }

    @Test
    void funnelUsesZeroForEmptyDenominatorsAndKeepsRatesBounded() {
        when(adminPermissionService.isAdmin(1L)).thenReturn(true);
        when(mapper.schemaReady()).thenReturn(14);
        CollaborationAnalyticsRows.FunnelRow row = new CollaborationAnalyticsRows.FunnelRow();
        row.setCreatedNeedCount(10L);
        row.setClaimedNeedCount(5L);
        row.setSubmittedNeedCount(0L);
        row.setAcceptedNeedCount(0L);
        row.setRejectedNeedCount(0L);
        row.setResubmittedNeedCount(0L);
        row.setReleasedNeedCount(0L);
        row.setReclaimedNeedCount(0L);
        row.setActiveClaimedNeedCount(0L);
        row.setStalledNeedCount(0L);
        row.setFollowedNeedCount(0L);
        row.setFollowedToClaimedNeedCount(0L);
        row.setMaintenanceTaskCount(4L);
        row.setCompletedMaintenanceTaskCount(3L);
        row.setAverageCreateToClaimSeconds(BigDecimal.valueOf(42));
        when(mapper.selectNeedFunnel(any(LocalDateTime.class), any(LocalDateTime.class),
                any(LocalDateTime.class), isNull())).thenReturn(row);

        CollaborationFunnelDTO funnel = service.needFunnel(1L, 30, null);

        assertEquals(new BigDecimal("0.5000"), funnel.getClaimRate());
        assertEquals(BigDecimal.ZERO.setScale(4), funnel.getSubmitRate());
        assertEquals(42L, funnel.getAverageCreateToClaimSeconds());
        assertEquals("UTC", funnel.getTimezone());
        assertEquals("0 when denominator is empty", funnel.getEmptyDenominatorPolicy());
        assertEquals(new BigDecimal("0.7500"), funnel.getMaintenanceCompletionRate());
    }

    @Test
    void domainModeratorMayReadOnlyTheirDomain() {
        when(adminPermissionService.isAdmin(8L)).thenReturn(false);
        when(adminPermissionService.hasRole(8L, AdminPermissionService.ROLE_OPS)).thenReturn(false);
        when(adminPermissionService.hasRole(8L, AdminPermissionService.ROLE_CONTENT_MODERATOR)).thenReturn(false);
        when(adminPermissionService.isLocalOpenMode()).thenReturn(false);
        when(domainModeratorService.canModerateDomain(8L, 2)).thenReturn(true);
        when(mapper.schemaReady()).thenReturn(14);
        when(mapper.selectNeedFunnel(any(LocalDateTime.class), any(LocalDateTime.class),
                any(LocalDateTime.class), org.mockito.ArgumentMatchers.eq(2)))
                .thenReturn(new CollaborationAnalyticsRows.FunnelRow());

        CollaborationFunnelDTO funnel = service.needFunnel(8L, 7, 2);

        assertEquals(2, funnel.getDomain());
        verify(domainModeratorService).canModerateDomain(8L, 2);
    }

    private static CollaborationAnalyticsRows.ContributionFactRow fact(String type, Long sourceId) {
        CollaborationAnalyticsRows.ContributionFactRow row =
                new CollaborationAnalyticsRows.ContributionFactRow();
        row.setFactType(type);
        row.setSourceId(sourceId);
        row.setDomain(1);
        row.setOccurredAt(LocalDateTime.of(2026, 7, 19, 0, 0));
        return row;
    }
}
