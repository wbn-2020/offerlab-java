package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.api.dto.ChannelQualityReviewBatchReassignActiveTasksCmd;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewBatchCoordinationRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewBatchCoordinationTaskRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewBatchMapper;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewBatchTaskStatusRow;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.post.api.ContentMaintenanceBatchTaskCoordinationResult;
import com.offerlab.community.post.api.ContentMaintenanceTaskCommandFacade;
import com.offerlab.community.post.application.DomainModeratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class ChannelQualityReviewBatchCoordinationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-07T12:00:00Z");

    @Mock
    private ChannelQualityReviewBatchMapper mapper;
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

    private ChannelQualityReviewBatchCoordinationService service;

    @BeforeEach
    void setUp() {
        service = new ChannelQualityReviewBatchCoordinationService(
                mapper,
                taskCommandFacade,
                idGenerator,
                adminAuditService,
                adminPermissionService,
                domainModeratorService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(mapper.batchTableExists()).thenReturn(1);
        when(mapper.coordinationColumnsExist()).thenReturn(2);
        when(mapper.taskTableExists()).thenReturn(1);
        when(mapper.attemptTableExists()).thenReturn(1);
        when(mapper.coordinationEventTableExists()).thenReturn(1);
        when(mapper.coordinationEventColumnsExist()).thenReturn(15);
        when(mapper.coordinationEventConstraintsExist()).thenReturn(4);
        when(mapper.coordinationEventWithdrawnIndexExists()).thenReturn(1);
    }

    private void stubAdminOperator() {
        when(adminPermissionService.isAdmin(9L)).thenReturn(true);
    }

    @Test
    void reassignRecordsFacadeSourceAssigneeInsteadOfOriginalDispatchAssignee() {
        stubAdminOperator();
        ChannelQualityReviewBatchCoordinationRow original = batch(7001L, 0, 11L);
        ChannelQualityReviewBatchCoordinationRow refreshed = batch(7001L, 1, 11L);
        ChannelQualityReviewBatchReassignActiveTasksCmd cmd =
                new ChannelQualityReviewBatchReassignActiveTasksCmd();
        cmd.setExpectedCoordinationVersion(0);
        cmd.setReplacementUid(33L);
        cmd.setNote("Capacity moved to another maintainer.");

        when(mapper.lockCoordinationById(7001L)).thenReturn(original);
        when(mapper.advanceCoordinationVersion(7001L, 0)).thenReturn(1);
        when(taskCommandFacade.reassignBatchActiveTasks(7001L, 1, 33L, 9L))
                .thenReturn(new ContentMaintenanceBatchTaskCoordinationResult(1, 2, 2, 22L));
        when(idGenerator.nextId()).thenReturn(8001L);
        when(mapper.insertCoordinationEvent(
                eq(8001L), eq(7001L), eq(9L), eq("ACTIVE_TASKS_REASSIGNED"),
                any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(mapper.selectCoordinationById(7001L)).thenReturn(refreshed);
        when(mapper.listTaskStatusCounts(List.of(7001L))).thenReturn(List.of(
                status(7001L, "OPEN", 1),
                status(7001L, "CLAIMED", 1)));
        when(mapper.listCoordinationTasks(7001L, 21)).thenReturn(List.of(
                task(9001L, "OPEN", 33L),
                task(9002L, "CLAIMED", 33L)));

        var result = service.reassignActiveTasks(7001L, cmd, 9L);

        ArgumentCaptor<Long> previousAssignee = ArgumentCaptor.forClass(Long.class);
        verify(mapper).insertCoordinationEvent(
                eq(8001L), eq(7001L), eq(9L), eq("ACTIVE_TASKS_REASSIGNED"),
                any(), any(), previousAssignee.capture(), eq(33L),
                any(), any(), eq(2), eq("Capacity moved to another maintainer."), eq(1));
        assertEquals(22L, previousAssignee.getValue());
        assertEquals(1, result.getCoordinationVersion());
        assertEquals(2, result.getReassignableTaskCount());
    }

    @Test
    void doesNotOfferDeadlineExtensionForLegacyBatchWithoutDeadline() {
        stubAdminOperator();
        ChannelQualityReviewBatchCoordinationRow legacy = batch(7002L, 0, 11L);
        legacy.setDueAt(null);
        legacy.setEffectiveDueAt(null);
        when(mapper.selectCoordinationById(7002L)).thenReturn(legacy);
        when(mapper.listTaskStatusCounts(List.of(7002L))).thenReturn(List.of(
                status(7002L, "OPEN", 1),
                status(7002L, "COMPLETED", 1)));
        when(mapper.listCoordinationTasks(7002L, 21)).thenReturn(List.of(
                task(9003L, "OPEN", 11L),
                task(9004L, "COMPLETED", 11L)));

        var result = service.coordination(7002L, 9L);

        assertEquals(false, result.getCanExtendDueAt());
        assertEquals("NOT_APPLICABLE", result.getDueState());
    }

    @Test
    void rejectsMalformedCoordinationVersion() {
        ChannelQualityReviewBatchCoordinationRow malformed = batch(7003L, -1, 11L);
        when(mapper.selectCoordinationById(7003L)).thenReturn(malformed);

        assertThrows(BizException.class, () -> service.coordination(7003L, 9L));
    }

    private static ChannelQualityReviewBatchCoordinationRow batch(
            long id, int version, long assigneeUid) {
        ChannelQualityReviewBatchCoordinationRow row = new ChannelQualityReviewBatchCoordinationRow();
        row.setId(id);
        row.setDomain(1);
        row.setSourceType("CHANNEL_HEALTH");
        row.setName("Batch execution");
        row.setAssigneeUid(assigneeUid);
        row.setDueAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).plusDays(3));
        row.setEffectiveDueAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).plusDays(3));
        row.setCoordinationVersion(version);
        row.setCandidateCount(2);
        row.setCreateTime(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        return row;
    }

    private static ChannelQualityReviewBatchTaskStatusRow status(
            long batchId, String status, long taskCount) {
        ChannelQualityReviewBatchTaskStatusRow row = new ChannelQualityReviewBatchTaskStatusRow();
        row.setBatchId(batchId);
        row.setStatus(status);
        row.setTaskCount(taskCount);
        return row;
    }

    private static ChannelQualityReviewBatchCoordinationTaskRow task(
            long taskId, String status, long assigneeUid) {
        ChannelQualityReviewBatchCoordinationTaskRow row =
                new ChannelQualityReviewBatchCoordinationTaskRow();
        row.setTaskId(taskId);
        row.setTitle("Public content");
        row.setStatus(status);
        row.setAssigneeUid(assigneeUid);
        row.setDueAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).plusDays(3));
        row.setUpdateTime(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        return row;
    }
}
