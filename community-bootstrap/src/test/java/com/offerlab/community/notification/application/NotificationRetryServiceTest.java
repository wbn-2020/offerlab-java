package com.offerlab.community.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.utils.LogMask;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.notification.infrastructure.persistence.mapper.NotificationRetryTaskMapper;
import com.offerlab.community.notification.infrastructure.persistence.po.NotificationRetryTaskPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationRetryServiceTest {

    @Mock
    private NotificationRetryTaskMapper taskMapper;
    @Mock
    private SnowflakeIdGenerator idGen;
    @Mock
    private NotificationFacadeImpl notificationFacade;

    private NotificationRetryService service;

    @BeforeEach
    void setUp() {
        service = new NotificationRetryService(taskMapper, idGen, new ObjectMapper(), notificationFacade);
        lenient().when(taskMapper.tableExists()).thenReturn(1);
    }

    @Test
    void enqueuePersistsRetryTaskWithDedupKeyAndErrorSummary() {
        when(idGen.nextId()).thenReturn(900L);

        service.enqueue("comment", 42L, 7L, 2, 2, 888L,
                Map.of("action", "comment", "postId", 888L), new IllegalStateException("provider timeout"));

        ArgumentCaptor<NotificationRetryTaskPO> captor = ArgumentCaptor.forClass(NotificationRetryTaskPO.class);
        verify(taskMapper).upsertPending(captor.capture());
        NotificationRetryTaskPO task = captor.getValue();
        assertEquals(900L, task.getId());
        assertEquals("42:7:2:2:888:comment", task.getDedupKey());
        assertEquals(NotificationRetryTaskMapper.STATUS_PENDING, task.getTaskStatus());
        assertEquals(0, task.getRetryCount());
        assertNotNull(task.getNextRetryTime());
        assertEquals("provider timeout", task.getLastError());
    }

    @Test
    void statusSummarizesAllRetryBucketsAndDueCount() {
        when(taskMapper.countByStatus()).thenReturn(List.of(
                Map.of("status", NotificationRetryTaskMapper.STATUS_PENDING, "count", 3L),
                Map.of("status", NotificationRetryTaskMapper.STATUS_DONE, "count", 4L),
                Map.of("status", NotificationRetryTaskMapper.STATUS_FAILED, "count", 5L),
                Map.of("status", NotificationRetryTaskMapper.STATUS_RUNNING, "count", 6L)
        ));
        when(taskMapper.countDuePending()).thenReturn(7L);
        NotificationRetryTaskPO failedTask = retryTask(2001L, 5, "{\"action\":\"comment\"}");
        failedTask.setScene("comment");
        failedTask.setLastError("notification table locked");
        failedTask.setNextRetryTime(LocalDateTime.parse("2026-06-08T23:10:00"));
        when(taskMapper.listRecent(NotificationRetryTaskMapper.STATUS_FAILED, 1)).thenReturn(List.of(failedTask));
        when(taskMapper.listRecent(NotificationRetryTaskMapper.STATUS_PENDING, 1)).thenReturn(List.of());

        Map<String, Object> status = service.status();

        @SuppressWarnings("unchecked")
        Map<String, Long> byStatus = (Map<String, Long>) status.get("byStatus");
        assertEquals("DEGRADED", status.get("status"));
        assertEquals(true, status.get("available"));
        assertEquals(true, status.get("attentionRequired"));
        assertEquals("Notification retry queue has failed or due tasks", status.get("message"));
        @SuppressWarnings("unchecked")
        Map<String, Object> diagnostics = (Map<String, Object>) status.get("diagnostics");
        @SuppressWarnings("unchecked")
        Map<String, Object> failedSample = (Map<String, Object>) diagnostics.get("failedSample");
        assertEquals(LogMask.id(2001L), failedSample.get("id"));
        assertEquals("comment", failedSample.get("scene"));
        assertEquals("notification table locked", diagnostics.get("latestError"));
        assertEquals("Open /api/v1/notification-ops/retry-tasks?status=2, confirm notification dependencies, then replay failed test records first.",
                diagnostics.get("recommendedAction"));
        assertEquals(3L, byStatus.get("pending"));
        assertEquals(4L, byStatus.get("done"));
        assertEquals(5L, byStatus.get("failed"));
        assertEquals(6L, byStatus.get("running"));
        assertEquals(7L, status.get("duePending"));
    }

    @Test
    void statusKeepsUpAndZeroBucketsWhenTableIsAvailableButEmpty() {
        when(taskMapper.countByStatus()).thenReturn(List.of());
        when(taskMapper.countDuePending()).thenReturn(0L);

        Map<String, Object> status = service.status();

        @SuppressWarnings("unchecked")
        Map<String, Long> byStatus = (Map<String, Long>) status.get("byStatus");
        assertEquals("UP", status.get("status"));
        assertEquals(true, status.get("available"));
        assertEquals(false, status.get("attentionRequired"));
        assertEquals(0L, byStatus.get("pending"));
        assertEquals(0L, byStatus.get("done"));
        assertEquals(0L, byStatus.get("failed"));
        assertEquals(0L, byStatus.get("running"));
        assertEquals(0L, status.get("duePending"));
    }

    @Test
    void statusReportsDownWhenTableIsUnavailable() {
        when(taskMapper.tableExists()).thenThrow(new IllegalStateException("down"));

        Map<String, Object> status = service.status();

        @SuppressWarnings("unchecked")
        Map<String, Long> byStatus = (Map<String, Long>) status.get("byStatus");
        assertEquals("DOWN", status.get("status"));
        assertEquals(false, status.get("available"));
        assertEquals(true, status.get("attentionRequired"));
        assertEquals("notification retry table unavailable", status.get("message"));
        assertEquals(0L, byStatus.get("pending"));
        assertEquals(0L, byStatus.get("failed"));
        assertEquals(0L, status.get("duePending"));
    }

    @Test
    void retryDueTasksClaimsDueRowsAndMarksSuccessfulReplayDone() {
        NotificationRetryTaskPO task = retryTask(1001L, 0, "{\"action\":\"comment\",\"postId\":888}");
        when(taskMapper.claimDue(anyString(), any(LocalDateTime.class), eq(50))).thenReturn(1);
        when(taskMapper.findClaimed(anyString(), eq(50))).thenReturn(List.of(task));

        service.retryDueTasks();

        verify(notificationFacade).createFromRetryTask(eq(42L), eq(7L), eq(2), eq(2), eq(888L), any());
        verify(taskMapper).markDone(eq(1001L), anyString());
        verify(taskMapper, never()).updateRetry(eq(1001L), anyString(), anyInt(), anyInt(), any(), any());
    }

    @Test
    void retryOneReschedulesFailedTaskBeforeRetryCap() {
        NotificationRetryTaskPO task = retryTask(1002L, 1, "{\"action\":\"comment\"}");
        doThrow(new IllegalStateException("provider down"))
                .when(notificationFacade)
                .createFromRetryTask(eq(42L), eq(7L), eq(2), eq(2), eq(888L), any());

        service.retryOne(task);

        verify(taskMapper).updateRetry(eq(1002L), anyString(), eq(NotificationRetryTaskMapper.STATUS_PENDING),
                eq(2), any(LocalDateTime.class), eq("provider down"));
        verify(taskMapper, never()).markDone(eq(1002L), anyString());
    }

    @Test
    void retryOneMarksTaskFailedAfterRetryCap() {
        NotificationRetryTaskPO task = retryTask(1003L, 4, "{\"action\":\"comment\"}");
        doThrow(new IllegalStateException("provider down"))
                .when(notificationFacade)
                .createFromRetryTask(eq(42L), eq(7L), eq(2), eq(2), eq(888L), any());

        service.retryOne(task);

        verify(taskMapper).updateRetry(eq(1003L), anyString(), eq(NotificationRetryTaskMapper.STATUS_FAILED),
                eq(5), isNull(), eq("provider down"));
        verify(taskMapper, never()).markDone(eq(1003L), anyString());
    }

    @Test
    void replayFailedBatchFiltersInvalidIdsAndDelegatesToMapper() {
        when(taskMapper.markFailedForRetryBatch(anyList())).thenReturn(2);

        int replayed = service.replayFailedBatch(Arrays.asList(3L, null, -1L, 3L, 4L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(taskMapper).markFailedForRetryBatch(idsCaptor.capture());
        assertEquals(List.of(3L, 4L), idsCaptor.getValue());
        assertEquals(2, replayed);
    }

    @Test
    void replayFailedReturnsFalseWhenTableIsUnavailable() {
        when(taskMapper.tableExists()).thenThrow(new IllegalStateException("down"));

        assertFalse(service.replayFailed(1L));
        assertEquals(0, service.replayFailedBatch(List.of(1L)));
    }

    private static NotificationRetryTaskPO retryTask(Long id, int retryCount, String contentJson) {
        NotificationRetryTaskPO task = new NotificationRetryTaskPO();
        task.setId(id);
        task.setReceiverUid(42L);
        task.setSenderUid(7L);
        task.setNotifType(2);
        task.setTargetType(2);
        task.setTargetId(888L);
        task.setContentJson(contentJson);
        task.setRetryCount(retryCount);
        task.setTaskStatus(NotificationRetryTaskMapper.STATUS_RUNNING);
        return task;
    }
}
