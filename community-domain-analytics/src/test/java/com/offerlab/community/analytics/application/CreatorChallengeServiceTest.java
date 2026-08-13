package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.api.dto.CreatorChallengeAdminActionCmd;
import com.offerlab.community.analytics.api.dto.CreatorChallengeAdminCmd;
import com.offerlab.community.analytics.api.dto.CreatorChallengeCompleteCmd;
import com.offerlab.community.analytics.api.dto.CreatorChallengeCompletionResultDTO;
import com.offerlab.community.analytics.api.dto.CreatorChallengeWorkspaceDTO;
import com.offerlab.community.analytics.infrastructure.persistence.mapper.CreatorGrowthChallengeMapper;
import com.offerlab.community.analytics.infrastructure.persistence.mapper.GrowthInsightMapper;
import com.offerlab.community.analytics.infrastructure.persistence.po.CreatorGrowthBadgePO;
import com.offerlab.community.analytics.infrastructure.persistence.po.CreatorGrowthChallengePO;
import com.offerlab.community.analytics.infrastructure.persistence.po.CreatorGrowthChallengeParticipationPO;
import com.offerlab.community.analytics.infrastructure.persistence.po.CreatorGrowthChallengeWorkspaceRow;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.security.AdminPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreatorChallengeServiceTest {

    @Mock
    private CreatorGrowthChallengeMapper challengeMapper;
    @Mock
    private GrowthInsightMapper growthInsightMapper;
    @Mock
    private SnowflakeIdGenerator idGenerator;
    @Mock
    private AdminPermissionService adminPermissionService;
    @Mock
    private AdminAuditService adminAuditService;

    private CreatorChallengeService creatorChallengeService;

    @BeforeEach
    void setUp() {
        creatorChallengeService = new CreatorChallengeService(
                challengeMapper,
                growthInsightMapper,
                idGenerator,
                adminPermissionService,
                adminAuditService);
    }

    @Test
    void workspaceQueriesOnlyTheCurrentUserAndExposesEligiblePostsOnlyForJoinedActiveChallenges() {
        LocalDateTime now = LocalDateTime.now();
        CreatorGrowthChallengeWorkspaceRow challenge = workspaceRow(
                11L, "JOINED", "PUBLISHED", now.minusHours(1), now.plusDays(1));
        when(challengeMapper.selectWorkspace(77L)).thenReturn(List.of(challenge));
        when(challengeMapper.selectAwardedBadges(77L)).thenReturn(List.of());
        when(growthInsightMapper.selectEligibleCreatorChallengePosts(
                eq(77L), any(LocalDateTime.class), any(LocalDateTime.class), eq(1), eq(15), eq(6)))
                .thenReturn(List.of(Map.of(
                        "postId", 901L,
                        "title", "A public reflection",
                        "domain", 1,
                        "postType", 15,
                        "publishedAt", now)));

        CreatorChallengeWorkspaceDTO workspace = creatorChallengeService.workspace(77L);

        assertEquals(1, workspace.getChallenges().size());
        assertEquals(11L, workspace.getChallenges().get(0).getId());
        assertEquals("JOINED", workspace.getChallenges().get(0).getParticipationStatus());
        assertEquals(1, workspace.getChallenges().get(0).getEligiblePosts().size());
        assertEquals(901L, workspace.getChallenges().get(0).getEligiblePosts().get(0).getPostId());
        verify(challengeMapper).selectWorkspace(77L);
        verify(challengeMapper).selectAwardedBadges(77L);
        verify(growthInsightMapper).selectEligibleCreatorChallengePosts(
                eq(77L), any(LocalDateTime.class), any(LocalDateTime.class), eq(1), eq(15), eq(6));
    }

    @Test
    void joinCreatesOnlyOneJoinedParticipationForAnActivePublishedChallenge() {
        LocalDateTime now = LocalDateTime.now();
        CreatorGrowthChallengePO challenge = activeChallenge(11L, now);
        when(challengeMapper.lockChallengeById(11L)).thenReturn(challenge);
        when(challengeMapper.lockParticipation(11L, 77L)).thenReturn(null);
        when(idGenerator.nextId()).thenReturn(7001L);
        when(challengeMapper.selectWorkspace(77L)).thenReturn(List.of(
                workspaceRow(11L, "JOINED", "PUBLISHED", challenge.getStartsAt(), challenge.getEndsAt())));
        when(growthInsightMapper.selectEligibleCreatorChallengePosts(
                eq(77L), any(LocalDateTime.class), any(LocalDateTime.class), eq(1), eq(15), eq(6)))
                .thenReturn(List.of());

        CreatorChallengeWorkspaceDTO.CreatorChallengeDTO result = creatorChallengeService.join(77L, 11L);

        ArgumentCaptor<CreatorGrowthChallengeParticipationPO> captor =
                ArgumentCaptor.forClass(CreatorGrowthChallengeParticipationPO.class);
        verify(challengeMapper).insertParticipation(captor.capture());
        assertEquals(7001L, captor.getValue().getId());
        assertEquals(11L, captor.getValue().getChallengeId());
        assertEquals(77L, captor.getValue().getUid());
        assertEquals(11L, result.getId());
        assertEquals("JOINED", result.getParticipationStatus());
    }

    @Test
    void joinRejectsAnInactiveOrPreviouslyWithdrawnChallenge() {
        CreatorGrowthChallengePO offline = activeChallenge(11L, LocalDateTime.now());
        offline.setStatus("OFFLINE");
        when(challengeMapper.lockChallengeById(11L)).thenReturn(offline);

        assertThrows(BizException.class, () -> creatorChallengeService.join(77L, 11L));
        verify(challengeMapper, never()).lockParticipation(any(), any());

        CreatorGrowthChallengePO active = activeChallenge(12L, LocalDateTime.now());
        CreatorGrowthChallengeParticipationPO withdrawn = new CreatorGrowthChallengeParticipationPO();
        withdrawn.setStatus("WITHDRAWN");
        when(challengeMapper.lockChallengeById(12L)).thenReturn(active);
        when(challengeMapper.lockParticipation(12L, 77L)).thenReturn(withdrawn);

        assertThrows(BizException.class, () -> creatorChallengeService.join(77L, 12L));
        verify(challengeMapper, never()).insertParticipation(any());
    }

    @Test
    void completionUsesTheCurrentUsersEligiblePublicPostAndAwardsOnlyNewThresholdBadges() {
        LocalDateTime now = LocalDateTime.now();
        CreatorGrowthChallengePO challenge = activeChallenge(11L, now);
        CreatorGrowthChallengeParticipationPO participation = new CreatorGrowthChallengeParticipationPO();
        participation.setId(8001L);
        participation.setStatus("JOINED");
        when(challengeMapper.lockChallengeById(11L)).thenReturn(challenge);
        when(challengeMapper.lockParticipation(11L, 77L)).thenReturn(participation);
        when(growthInsightMapper.selectCreatorChallengeCompletionPost(77L, 901L))
                .thenReturn(Map.of(
                        "postId", 901L,
                        "publishedAt", now,
                        "domain", 1,
                        "postType", 15));
        when(challengeMapper.completeParticipation(8001L, 77L, 901L)).thenReturn(1);
        when(challengeMapper.countCompletedChallenges(77L)).thenReturn(3L);
        when(challengeMapper.selectEligibleBadges(3L)).thenReturn(List.of(
                badge(1L, "PUBLIC_CREATOR_STARTER", 1),
                badge(2L, "PUBLIC_CREATOR_STEADY", 3)));
        when(idGenerator.nextId()).thenReturn(9001L, 9002L);
        when(challengeMapper.insertBadgeAward(any())).thenReturn(1);
        when(challengeMapper.selectWorkspace(77L)).thenReturn(List.of(
                workspaceRow(11L, "COMPLETED", "PUBLISHED", challenge.getStartsAt(), challenge.getEndsAt())));

        CreatorChallengeCompletionResultDTO result = creatorChallengeService.complete(
                77L, 11L, CreatorChallengeCompleteCmd.builder().postId(901L).build());

        assertFalse(result.isReplayed());
        assertEquals("COMPLETED", result.getChallenge().getParticipationStatus());
        assertEquals(2, result.getNewlyAwardedBadges().size());
        verify(challengeMapper).completeParticipation(8001L, 77L, 901L);
        verify(challengeMapper, times(2)).insertBadgeAward(any());
    }

    @Test
    void completionRejectsUnjoinedOrIneligiblePostsAndReplaysCompletedParticipationWithoutAnotherAward() {
        LocalDateTime now = LocalDateTime.now();
        CreatorGrowthChallengePO challenge = activeChallenge(11L, now);
        when(challengeMapper.lockChallengeById(11L)).thenReturn(challenge);
        when(challengeMapper.lockParticipation(11L, 77L)).thenReturn(null);

        assertThrows(BizException.class, () -> creatorChallengeService.complete(
                77L, 11L, CreatorChallengeCompleteCmd.builder().postId(901L).build()));

        CreatorGrowthChallengeParticipationPO joined = new CreatorGrowthChallengeParticipationPO();
        joined.setId(8001L);
        joined.setStatus("JOINED");
        when(challengeMapper.lockParticipation(11L, 77L)).thenReturn(joined);
        when(growthInsightMapper.selectCreatorChallengeCompletionPost(77L, 901L)).thenReturn(null);

        assertThrows(BizException.class, () -> creatorChallengeService.complete(
                77L, 11L, CreatorChallengeCompleteCmd.builder().postId(901L).build()));
        verify(challengeMapper, never()).completeParticipation(any(), any(), any());

        CreatorGrowthChallengeParticipationPO completed = new CreatorGrowthChallengeParticipationPO();
        completed.setId(8001L);
        completed.setStatus("COMPLETED");
        when(challengeMapper.lockParticipation(11L, 77L)).thenReturn(completed);
        when(challengeMapper.selectWorkspace(77L)).thenReturn(List.of(
                workspaceRow(11L, "COMPLETED", "PUBLISHED", challenge.getStartsAt(), challenge.getEndsAt())));

        CreatorChallengeCompletionResultDTO replay = creatorChallengeService.complete(
                77L, 11L, CreatorChallengeCompleteCmd.builder().postId(901L).build());

        assertEquals(true, replay.isReplayed());
        assertEquals(0, replay.getNewlyAwardedBadges().size());
        verify(challengeMapper, never()).selectEligibleBadges(any(Long.class));
    }

    @Test
    void operationsRequiresOpsAndRejectsEditingPublishedOrPublishingExpiredChallenges() {
        doThrow(new BizException(403, "FORBIDDEN"))
                .when(adminPermissionService)
                .requireScope(77L, AdminPermissionService.ROLE_OPS);

        assertThrows(BizException.class, () -> creatorChallengeService.adminChallenges(77L));
        verify(challengeMapper, never()).selectAdminChallenges(any(Integer.class));

        CreatorGrowthChallengePO published = activeChallenge(11L, LocalDateTime.now());
        published.setStatus("PUBLISHED");
        when(challengeMapper.lockChallengeById(11L)).thenReturn(published);
        CreatorChallengeAdminCmd update = CreatorChallengeAdminCmd.builder()
                .id(11L)
                .challengeCode("PUBLIC_WRITING_WEEK")
                .title("Updated title")
                .description("Updated public challenge description.")
                .domain(1)
                .postType(15)
                .startsAt(LocalDateTime.now().minusDays(1))
                .endsAt(LocalDateTime.now().plusDays(1))
                .reason("Correct title")
                .build();

        assertThrows(BizException.class, () -> creatorChallengeService.upsert(88L, update));
        verify(challengeMapper, never()).updateDraftChallenge(any());

        CreatorGrowthChallengePO expiredDraft = activeChallenge(12L, LocalDateTime.now());
        expiredDraft.setStatus("DRAFT");
        expiredDraft.setStartsAt(LocalDateTime.now().minusDays(2));
        expiredDraft.setEndsAt(LocalDateTime.now().minusHours(1));
        when(challengeMapper.lockChallengeById(12L)).thenReturn(expiredDraft);

        assertThrows(BizException.class, () -> creatorChallengeService.publish(
                88L, 12L, CreatorChallengeAdminActionCmd.builder().reason("Challenge window expired").build()));
        verify(challengeMapper, never()).publishChallenge(any(), any());
    }

    private static CreatorGrowthChallengePO activeChallenge(Long id, LocalDateTime now) {
        CreatorGrowthChallengePO challenge = new CreatorGrowthChallengePO();
        challenge.setId(id);
        challenge.setChallengeCode("PUBLIC_WRITING_WEEK");
        challenge.setTitle("Public writing week");
        challenge.setDescription("Publish one public reflection.");
        challenge.setDomain(1);
        challenge.setPostType(15);
        challenge.setStatus("PUBLISHED");
        challenge.setStartsAt(now.minusHours(1));
        challenge.setEndsAt(now.plusDays(1));
        return challenge;
    }

    private static CreatorGrowthChallengeWorkspaceRow workspaceRow(
            Long id,
            String participationStatus,
            String status,
            LocalDateTime startsAt,
            LocalDateTime endsAt) {
        CreatorGrowthChallengeWorkspaceRow row = new CreatorGrowthChallengeWorkspaceRow();
        row.setId(id);
        row.setChallengeCode("PUBLIC_WRITING_WEEK");
        row.setTitle("Public writing week");
        row.setDescription("Publish one public reflection.");
        row.setDomain(1);
        row.setPostType(15);
        row.setStatus(status);
        row.setStartsAt(startsAt);
        row.setEndsAt(endsAt);
        row.setParticipationStatus(participationStatus);
        return row;
    }

    private static CreatorGrowthBadgePO badge(Long id, String code, int requirement) {
        CreatorGrowthBadgePO badge = new CreatorGrowthBadgePO();
        badge.setId(id);
        badge.setBadgeCode(code);
        badge.setTitle(code);
        badge.setDescription("Challenge completion boundary.");
        badge.setRequiredCompletedChallengeCount(requirement);
        badge.setEnabled(1);
        return badge;
    }
}
