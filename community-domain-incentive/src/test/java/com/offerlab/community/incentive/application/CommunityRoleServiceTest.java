package com.offerlab.community.incentive.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.incentive.api.IncentiveDtos.RoleEvidenceDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.RoleReviewContextDTO;
import com.offerlab.community.incentive.infrastructure.IncentiveMapper;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.RoleApplicationPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.RoleDefinitionPO;
import com.offerlab.community.incentive.infrastructure.IncentivePersistence.RoleGrantPO;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.CommunityRoleAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityRoleServiceTest {

    @Mock
    private IncentiveMapper mapper;
    @Mock
    private AdminPermissionService permissions;
    @Mock
    private CommunityRoleAccessService roleAccess;
    @Mock
    private AdminAuditService audit;

    private CommunityRoleService service;

    @BeforeEach
    void setUp() {
        service = new CommunityRoleService(
                mapper,
                new SnowflakeIdGenerator(),
                permissions,
                roleAccess,
                audit,
                new ObjectMapper());
    }

    @Test
    void selfEvidenceUsesCurrentGrantAndKeepsMaintenanceCapabilityServerAuthoritative() {
        RoleDefinitionPO definition = definition();
        RoleGrantPO grant = grant();
        when(mapper.selectRoleDefinition("CHANNEL_RESOURCE_MAINTAINER", "TECH"))
                .thenReturn(definition);
        when(mapper.selectRoleMetrics(22L, "TECH")).thenReturn(eligibleMetrics());
        when(mapper.selectLatestUserRoleApplication(22L, "CHANNEL_RESOURCE_MAINTAINER", "TECH"))
                .thenReturn(null);
        when(mapper.selectLatestUserRoleGrant(22L, "CHANNEL_RESOURCE_MAINTAINER", "TECH"))
                .thenReturn(grant);
        when(roleAccess.hasActiveGrant(22L, "CHANNEL_RESOURCE_MAINTAINER", "TECH"))
                .thenReturn(true);

        RoleEvidenceDTO evidence =
                service.evidence(22L, "channel_resource_maintainer", "tech");

        assertTrue(evidence.getEligible());
        assertFalse(evidence.getCanApply());
        assertTrue(evidence.getCanUseMaintenanceWorkspace());
        assertEquals("ACTIVE", evidence.getGrantStatus());
        assertTrue(evidence.getAvailableActions().contains("OPEN_MAINTENANCE_CANDIDATES"));
    }

    @Test
    void adminReviewContextReturnsAggregatesAndAuditsSensitiveRead() {
        RoleApplicationPO application = new RoleApplicationPO();
        application.setId(31L);
        application.setApplicantUid(22L);
        application.setRoleCode("CHANNEL_RESOURCE_MAINTAINER");
        application.setDomainCode("TECH");
        application.setStatement("I maintain public resources.");
        application.setEligibilitySnapshotJson("{}");
        application.setApplicationStatus("SUBMITTED");
        RoleDefinitionPO definition = definition();
        RoleGrantPO grant = grant();

        when(mapper.selectRoleApplication(31L)).thenReturn(application);
        when(mapper.selectRoleDefinition("CHANNEL_RESOURCE_MAINTAINER", "TECH"))
                .thenReturn(definition);
        when(mapper.selectRoleMetrics(22L, "TECH")).thenReturn(eligibleMetrics());
        when(mapper.selectLatestUserRoleGrant(22L, "CHANNEL_RESOURCE_MAINTAINER", "TECH"))
                .thenReturn(grant);
        when(mapper.selectRoleReviewContributionSummary(22L, 1)).thenReturn(Map.of(
                "recentTrustedContributionCount", 12L,
                "completedMaintenanceTaskCount", 4L,
                "returnedMaintenanceTaskCount", 1L
        ));
        when(mapper.selectRoleGrantHistory(41L, 50)).thenReturn(List.of(Map.of(
                "id", 51L,
                "grantId", 41L,
                "toStatus", "ACTIVE",
                "operatorUid", 8L,
                "actionReason", "approved",
                "createTime", LocalDateTime.of(2026, 7, 19, 8, 0)
        )));
        when(roleAccess.hasActiveGrant(22L, "CHANNEL_RESOURCE_MAINTAINER", "TECH"))
                .thenReturn(true);

        RoleReviewContextDTO context =
                service.reviewContext(31L, "manual role review", 8L);

        assertEquals(12L, context.getRecentTrustedContributionCount());
        assertEquals(4L, context.getCompletedMaintenanceTaskCount());
        assertEquals(1L, context.getReturnedMaintenanceTaskCount());
        assertEquals(1, context.getGrantHistory().size());
        verify(permissions).requireAdmin(8L);
        verify(audit).recordRequired(eq(8L), eq("COMMUNITY_ROLE_REVIEW_CONTEXT_VIEW"),
                eq("COMMUNITY_ROLE_APPLICATION"), eq(31L), eq(null), any(),
                eq("manual role review"));
    }

    @Test
    void expiredRolePreviewIsBoundedAndRequiresAdmin() {
        when(mapper.selectExpiredGrantIds(25)).thenReturn(List.of(1L, 2L));

        int count = service.countExpireDue(8L, 25);

        assertEquals(2, count);
        verify(permissions).requireAdmin(8L);
        verify(mapper).selectExpiredGrantIds(25);
    }

    private static RoleDefinitionPO definition() {
        RoleDefinitionPO definition = new RoleDefinitionPO();
        definition.setId(11L);
        definition.setRoleCode("CHANNEL_RESOURCE_MAINTAINER");
        definition.setRoleName("Channel resource maintainer");
        definition.setDescription("Maintains low-risk public resources.");
        definition.setDomainCode("TECH");
        definition.setMinAccountAgeDays(30);
        definition.setMinDomainReputation(80L);
        definition.setMinActivityCount(8);
        definition.setMaxViolationCount(0);
        definition.setMinCurationAccuracyBps(0);
        definition.setRequiresNoRiskFreeze(1);
        definition.setEnabled(1);
        return definition;
    }

    private static RoleGrantPO grant() {
        RoleGrantPO grant = new RoleGrantPO();
        grant.setId(41L);
        grant.setUserId(22L);
        grant.setRoleCode("CHANNEL_RESOURCE_MAINTAINER");
        grant.setDomainCode("TECH");
        grant.setGrantStatus("ACTIVE");
        grant.setGrantedBy(8L);
        grant.setGrantReason("approved");
        grant.setGrantedAt(LocalDateTime.of(2026, 7, 18, 8, 0));
        grant.setExpiresAt(LocalDateTime.of(2099, 1, 1, 8, 0));
        return grant;
    }

    private static Map<String, Object> eligibleMetrics() {
        return Map.of(
                "accountAgeDays", 120,
                "domainReputation", 500L,
                "activityCount", 20,
                "violationCount", 0,
                "curationAccuracyBps", 9000,
                "riskFrozen", 0
        );
    }
}
