package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.api.dto.ChannelQualityReviewBatchCandidateCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewBatchCreateCmd;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewBatchMapper;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewBatchRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewBatchTaskRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewBatchTaskStatusRow;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.post.api.ContentMaintenanceTaskCommandFacade;
import com.offerlab.community.post.application.DomainModeratorService;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceTaskDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChannelQualityReviewBatchServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");

    @Mock
    private ChannelQualityReviewBatchMapper mapper;
    @Mock
    private ChannelQualityReviewCandidateService candidateService;
    @Mock
    private ContentMaintenanceTaskCommandFacade taskCommandFacade;
    @Mock
    private SnowflakeIdGenerator idGenerator;
    @Mock
    private AdminAuditService adminAuditService;
    @Mock
    private AdminPermissionService adminPermissionService;
    @Mock
    private DomainModeratorService domainModeratorService;

    private ChannelQualityReviewBatchService service;

    @BeforeEach
    void setUp() {
        service = new ChannelQualityReviewBatchService(
                mapper,
                candidateService,
                taskCommandFacade,
                idGenerator,
                adminAuditService,
                adminPermissionService,
                domainModeratorService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(mapper.batchTableExists()).thenReturn(1);
        when(mapper.taskTableExists()).thenReturn(1);
        when(mapper.attemptTableExists()).thenReturn(1);
        when(adminPermissionService.isAdmin(9L)).thenReturn(true);
    }

    @Test
    void createsOneAtomicDispatchBatchAndReturnsItsTaskProjection() {
        ChannelQualityReviewBatchCreateCmd cmd = createCmd(1, 1002L, 7L);
        when(candidateService.resolveReadyForDispatch(
                eq(1), any(), eq(9L))).thenReturn(List.of(
                new ChannelQualityReviewCandidateService.DispatchableCandidate(
                        1002L, 7L, 1, "公开帖子")));
        when(idGenerator.nextId()).thenReturn(7001L);
        when(mapper.insert(
                7001L, 1, "CHANNEL_HEALTH", "第一批", 11L, "HIGH",
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).plusDays(3), 9L, 1)).thenReturn(1);
        when(taskCommandFacade.dispatchChannelHealthTask(any(), eq(9L))).thenReturn(
                ContentMaintenanceTaskDTO.builder()
                        .id(8001L)
                        .domain(1)
                        .sourceType("CHANNEL_HEALTH")
                        .sourcePostId(1002L)
                        .sourceRefId(7L)
                        .dispatchBatchId(7001L)
                        .status("OPEN")
                        .build());
        when(mapper.selectById(7001L)).thenReturn(batch(7001L, 1, 1));
        when(mapper.listTaskStatusCounts(List.of(7001L))).thenReturn(List.of(
                count(7001L, "OPEN", 1)));
        when(mapper.listTasksByBatchId(7001L, 21)).thenReturn(List.of(
                task(8001L, 7001L, 1002L, 7L, "OPEN", null, null)));

        var result = service.create(cmd, 9L);

        assertEquals(7001L, result.getId());
        assertEquals("ACTION_REQUIRED", result.getProgressState());
        assertEquals("ON_TRACK", result.getDueState());
        assertEquals(1, result.getTasks().size());
        assertEquals("OPEN", result.getTasks().get(0).getMaintenancePhase());
        verify(taskCommandFacade).dispatchChannelHealthTask(any(), eq(9L));
        verify(adminAuditService).recordRequired(
                eq(9L),
                eq("CHANNEL_QUALITY_REVIEW_BATCH_CREATE"),
                eq("CHANNEL_QUALITY_REVIEW_BATCH"),
                eq(7001L),
                any(),
                any(),
                any());
    }

    @Test
    void detailUsesReworkPhaseAndDueSoonForActiveBatch() {
        ChannelQualityReviewBatchRow batch = batch(7002L, 1, 2);
        batch.setDueAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).plusDays(1));
        when(mapper.selectById(7002L)).thenReturn(batch);
        when(mapper.listTaskStatusCounts(List.of(7002L))).thenReturn(List.of(
                count(7002L, "CLAIMED", 1),
                count(7002L, "CLOSED", 1)));
        when(mapper.listTasksByBatchId(7002L, 21)).thenReturn(List.of(
                task(8002L, 7002L, 1003L, 8L, "CLAIMED", null, "REJECTED"),
                task(8003L, 7002L, 1004L, 9L, "CLOSED", null, "CLOSED")));

        var result = service.get(7002L, 9L);

        assertEquals("IN_PROGRESS", result.getProgressState());
        assertEquals("DUE_SOON", result.getDueState());
        assertEquals("REWORK", result.getTasks().get(0).getMaintenancePhase());
        assertEquals("CLOSED", result.getTasks().get(1).getMaintenancePhase());
    }

    @Test
    void rejectsRepeatedSourceRevisionBeforeAnyCandidateResolution() {
        ChannelQualityReviewBatchCreateCmd cmd = createCmd(1, 1002L, 7L);
        cmd.setCandidates(List.of(candidate(1002L, 7L), candidate(1002L, 7L)));

        assertThrows(BizException.class, () -> service.create(cmd, 9L));
    }

    private static ChannelQualityReviewBatchCreateCmd createCmd(
            int domain, long sourcePostId, long sourceRefId) {
        ChannelQualityReviewBatchCreateCmd cmd = new ChannelQualityReviewBatchCreateCmd();
        cmd.setDomain(domain);
        cmd.setName("第一批");
        cmd.setAssigneeUid(11L);
        cmd.setPriority("HIGH");
        cmd.setDueInDays(3);
        cmd.setCandidates(List.of(candidate(sourcePostId, sourceRefId)));
        return cmd;
    }

    private static ChannelQualityReviewBatchCandidateCmd candidate(long sourcePostId, long sourceRefId) {
        ChannelQualityReviewBatchCandidateCmd candidate = new ChannelQualityReviewBatchCandidateCmd();
        candidate.setSourcePostId(sourcePostId);
        candidate.setSourceRefId(sourceRefId);
        return candidate;
    }

    private static ChannelQualityReviewBatchRow batch(long id, int domain, int candidateCount) {
        ChannelQualityReviewBatchRow row = new ChannelQualityReviewBatchRow();
        row.setId(id);
        row.setDomain(domain);
        row.setSourceType("CHANNEL_HEALTH");
        row.setName("第一批");
        row.setAssigneeUid(11L);
        row.setCreatedByUid(9L);
        row.setPriority("HIGH");
        row.setDueAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).plusDays(3));
        row.setCandidateCount(candidateCount);
        row.setCreateTime(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        return row;
    }

    private static ChannelQualityReviewBatchTaskStatusRow count(long batchId, String status, long count) {
        ChannelQualityReviewBatchTaskStatusRow row = new ChannelQualityReviewBatchTaskStatusRow();
        row.setBatchId(batchId);
        row.setStatus(status);
        row.setTaskCount(count);
        return row;
    }

    private static ChannelQualityReviewBatchTaskRow task(
            long taskId,
            long batchId,
            long sourcePostId,
            long sourceRefId,
            String status,
            String terminalOutcome,
            String latestAttemptDecision) {
        ChannelQualityReviewBatchTaskRow row = new ChannelQualityReviewBatchTaskRow();
        row.setTaskId(taskId);
        row.setBatchId(batchId);
        row.setSourcePostId(sourcePostId);
        row.setSourceRefId(sourceRefId);
        row.setTitle("公开帖子");
        row.setStatus(status);
        row.setTerminalOutcomeCode(terminalOutcome);
        row.setLatestAttemptDecision(latestAttemptDecision);
        return row;
    }
}
