package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseAcknowledgeCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseAssignOwnerCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseCloseCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewRiskCaseCreateCmd;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewBatchCoordinationRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewBatchMapper;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewBatchRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewBatchTaskStatusRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewRiskCaseMapper;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewRiskCaseRiskNoteEventRow;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewRiskCaseRow;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.post.application.DomainModeratorService;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChannelQualityReviewRiskCaseServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-07T12:00:00Z");
    private static final long OPERATOR_UID = 9L;
    private static final long BATCH_ID = 7001L;
    private static final long CASE_ID = 9001L;
    private static final long EVENT_ID = 9002L;
    private static final long RISK_EVENT_ID = 8001L;

    @Mock
    private ChannelQualityReviewRiskCaseMapper riskCaseMapper;
    @Mock
    private ChannelQualityReviewBatchMapper batchMapper;
    @Mock
    private SnowflakeIdGenerator idGenerator;
    @Mock
    private AdminAuditService adminAuditService;
    @Mock
    private AdminPermissionService adminPermissionService;
    @Mock
    private DomainModeratorService domainModeratorService;
    @Mock
    private ChannelQualityReviewRiskCaseGovernanceService governanceService;

    private ChannelQualityReviewRiskCaseService service;

    @BeforeEach
    void setUp() {
        service = new ChannelQualityReviewRiskCaseService(
                riskCaseMapper,
                batchMapper,
                idGenerator,
                adminAuditService,
                adminPermissionService,
                domainModeratorService,
                governanceService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(riskCaseMapper.caseTableExists()).thenReturn(1);
        when(riskCaseMapper.eventTableExists()).thenReturn(1);
        when(riskCaseMapper.caseColumnsExist()).thenReturn(17);
        when(riskCaseMapper.eventColumnsExist()).thenReturn(12);
        when(riskCaseMapper.caseConstraintsExist()).thenReturn(7);
        when(riskCaseMapper.eventConstraintsExist()).thenReturn(4);
        when(riskCaseMapper.caseIndexesExist()).thenReturn(7);
        when(riskCaseMapper.eventIndexesExist()).thenReturn(3);
        when(batchMapper.batchTableExists()).thenReturn(1);
        when(batchMapper.taskTableExists()).thenReturn(1);
        when(batchMapper.coordinationEventTableExists()).thenReturn(1);
        when(batchMapper.coordinationColumnsExist()).thenReturn(2);
        when(adminPermissionService.isAdmin(OPERATOR_UID)).thenReturn(true);
    }

    @Test
    void createWritesCaseOpenedEventAndRequiredAudit() {
        ChannelQualityReviewBatchCoordinationRow batch = batch(2, NOW.plusSeconds(3 * 24 * 60 * 60L));
        ChannelQualityReviewRiskCaseCreateCmd cmd = createCmd(2, RISK_EVENT_ID);

        when(batchMapper.lockCoordinationById(BATCH_ID)).thenReturn(batch);
        when(riskCaseMapper.selectRiskNoteEvent(BATCH_ID, RISK_EVENT_ID))
                .thenReturn(riskNote(RISK_EVENT_ID, BATCH_ID, 2));
        when(riskCaseMapper.insertCase(
                anyLong(), anyLong(), anyInt(), anyString(), any(), any(), any(), anyString(),
                any(), anyInt(), anyInt(), anyLong())).thenReturn(1);
        when(idGenerator.nextId()).thenReturn(CASE_ID, EVENT_ID);
        when(riskCaseMapper.insertEvent(
                anyLong(), anyLong(), anyLong(), anyLong(), anyString(), any(), anyString(),
                any(), anyInt(), anyInt(), anyString())).thenReturn(1);
        stubDetail(openRiskCase(CASE_ID, "RISK_EVENT", "OPEN", null, 1, 2));

        var result = service.create(BATCH_ID, cmd, OPERATOR_UID);

        verify(riskCaseMapper).insertCase(
                eq(CASE_ID), eq(BATCH_ID), eq(1), eq("RISK_EVENT"), eq(RISK_EVENT_ID),
                eq("BLOCKER"), isNull(), eq("OPEN"), isNull(), eq(1), eq(2), eq(OPERATOR_UID));
        verify(riskCaseMapper).insertEvent(
                eq(EVENT_ID), eq(CASE_ID), eq(BATCH_ID), eq(OPERATOR_UID), eq("CASE_OPENED"),
                isNull(), eq("OPEN"), isNull(), eq(2), eq(1), eq("Risk requires coordination."));
        verify(adminAuditService).recordRequired(
                eq(OPERATOR_UID), eq("CHANNEL_QUALITY_REVIEW_RISK_CASE_CREATE"),
                eq("CHANNEL_QUALITY_REVIEW_RISK_CASE"), eq(CASE_ID), eq(Map.of()),
                eq(Map.of("batchId", BATCH_ID, "triggerType", "RISK_EVENT")),
                eq("channel quality review risk case updated"));
        assertEquals(CASE_ID, result.getId());
        assertEquals("OPEN", result.getStatus());
    }

    @Test
    void createRejectsSecondActiveCaseForBatch() {
        ChannelQualityReviewBatchCoordinationRow batch = batch(2, NOW.plusSeconds(3 * 24 * 60 * 60L));
        when(batchMapper.lockCoordinationById(BATCH_ID)).thenReturn(batch);
        when(riskCaseMapper.selectRiskNoteEvent(BATCH_ID, RISK_EVENT_ID))
                .thenReturn(riskNote(RISK_EVENT_ID, BATCH_ID, 2));
        when(riskCaseMapper.selectActiveByBatchId(BATCH_ID))
                .thenReturn(openRiskCase(9101L, "RISK_EVENT", "OPEN", null, 1, 2));

        BizException exception = assertThrows(
                BizException.class, () -> service.create(BATCH_ID, createCmd(2, RISK_EVENT_ID), OPERATOR_UID));

        assertEquals(ErrorCode.DUPLICATE_OPERATION.getCode(), exception.getCode());
        verify(riskCaseMapper, never()).insertCase(
                anyLong(), anyLong(), anyInt(), anyString(), any(), any(), any(), anyString(),
                any(), anyInt(), anyInt(), anyLong());
        verify(adminAuditService, never()).recordRequired(
                anyLong(), anyString(), anyString(), any(), any(), any(), anyString());
    }

    @Test
    void createRejectsRiskEventOutsideCurrentBatch() {
        ChannelQualityReviewBatchCoordinationRow batch = batch(2, NOW.plusSeconds(3 * 24 * 60 * 60L));
        when(batchMapper.lockCoordinationById(BATCH_ID)).thenReturn(batch);
        when(riskCaseMapper.selectRiskNoteEvent(BATCH_ID, RISK_EVENT_ID))
                .thenReturn(riskNote(RISK_EVENT_ID, BATCH_ID + 1, 2));

        BizException exception = assertThrows(
                BizException.class, () -> service.create(BATCH_ID, createCmd(2, RISK_EVENT_ID), OPERATOR_UID));

        assertEquals(ErrorCode.INVALID_STATUS.getCode(), exception.getCode());
        verify(riskCaseMapper, never()).insertCase(
                anyLong(), anyLong(), anyInt(), anyString(), any(), any(), any(), anyString(),
                any(), anyInt(), anyInt(), anyLong());
    }

    @Test
    void assignOwnerRejectsModeratorIneligibleForBatchDomain() {
        ChannelQualityReviewRiskCaseRow riskCase =
                openRiskCase(CASE_ID, "RISK_EVENT", "OPEN", null, 1, 2);
        ChannelQualityReviewRiskCaseAssignOwnerCmd cmd = new ChannelQualityReviewRiskCaseAssignOwnerCmd();
        cmd.setExpectedCaseVersion(1);
        cmd.setOwnerUid(33L);
        cmd.setNote("Transfer responsibility.");
        when(riskCaseMapper.lockById(CASE_ID)).thenReturn(riskCase);
        when(batchMapper.selectCoordinationById(BATCH_ID))
                .thenReturn(batch(2, NOW.plusSeconds(3 * 24 * 60 * 60L)));

        BizException exception = assertThrows(
                BizException.class, () -> service.assignOwner(CASE_ID, cmd, OPERATOR_UID));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
        verify(riskCaseMapper, never()).updateCase(
                anyLong(), anyInt(), anyString(), any());
        verify(riskCaseMapper, never()).insertEvent(
                anyLong(), anyLong(), anyLong(), anyLong(), anyString(), any(), anyString(),
                any(), anyInt(), anyInt(), anyString());
    }

    @Test
    void acknowledgeRejectsStaleCaseVersion() {
        ChannelQualityReviewRiskCaseRow riskCase =
                openRiskCase(CASE_ID, "RISK_EVENT", "OPEN", 11L, 3, 2);
        ChannelQualityReviewRiskCaseAcknowledgeCmd cmd = new ChannelQualityReviewRiskCaseAcknowledgeCmd();
        cmd.setExpectedCaseVersion(2);
        cmd.setNote("Acknowledged by owner.");
        when(riskCaseMapper.lockById(CASE_ID)).thenReturn(riskCase);
        when(batchMapper.selectCoordinationById(BATCH_ID))
                .thenReturn(batch(2, NOW.plusSeconds(3 * 24 * 60 * 60L)));

        BizException exception = assertThrows(
                BizException.class, () -> service.acknowledge(CASE_ID, cmd, OPERATOR_UID));

        assertEquals(ErrorCode.CONCURRENT_MODIFICATION.getCode(), exception.getCode());
        verify(riskCaseMapper, never()).updateCase(
                anyLong(), anyInt(), anyString(), any());
    }

    @Test
    void acknowledgeRejectsInvalidStateTransition() {
        ChannelQualityReviewRiskCaseRow riskCase =
                openRiskCase(CASE_ID, "RISK_EVENT", "ACKNOWLEDGED", 11L, 3, 2);
        ChannelQualityReviewRiskCaseAcknowledgeCmd cmd = new ChannelQualityReviewRiskCaseAcknowledgeCmd();
        cmd.setExpectedCaseVersion(3);
        cmd.setNote("Second acknowledgement.");
        when(riskCaseMapper.lockById(CASE_ID)).thenReturn(riskCase);
        when(batchMapper.selectCoordinationById(BATCH_ID))
                .thenReturn(batch(2, NOW.plusSeconds(3 * 24 * 60 * 60L)));

        BizException exception = assertThrows(
                BizException.class, () -> service.acknowledge(CASE_ID, cmd, OPERATOR_UID));

        assertEquals(ErrorCode.INVALID_STATUS.getCode(), exception.getCode());
        verify(riskCaseMapper, never()).updateCase(
                anyLong(), anyInt(), anyString(), any());
        verify(adminAuditService, never()).recordRequired(
                anyLong(), anyString(), anyString(), any(), any(), any(), anyString());
    }

    @Test
    void acknowledgeRejectsOpenCaseWithoutAssignedOwner() {
        ChannelQualityReviewRiskCaseRow riskCase =
                openRiskCase(CASE_ID, "RISK_EVENT", "OPEN", null, 1, 2);
        ChannelQualityReviewRiskCaseAcknowledgeCmd cmd = new ChannelQualityReviewRiskCaseAcknowledgeCmd();
        cmd.setExpectedCaseVersion(1);
        cmd.setNote("Attempting acknowledgement without an owner.");
        when(riskCaseMapper.lockById(CASE_ID)).thenReturn(riskCase);
        when(batchMapper.selectCoordinationById(BATCH_ID))
                .thenReturn(batch(2, NOW.plusSeconds(3 * 24 * 60 * 60L)));

        BizException exception = assertThrows(
                BizException.class, () -> service.acknowledge(CASE_ID, cmd, OPERATOR_UID));

        assertEquals(ErrorCode.INVALID_STATUS.getCode(), exception.getCode());
        verify(riskCaseMapper, never()).updateCase(
                anyLong(), anyInt(), anyString(), any());
        verify(riskCaseMapper, never()).insertEvent(
                anyLong(), anyLong(), anyLong(), anyLong(), anyString(), any(), anyString(),
                any(), anyInt(), anyInt(), anyString());
    }

    @Test
    void closeRejectsLegacyV40CloseContract() {
        ChannelQualityReviewRiskCaseCloseCmd cmd = new ChannelQualityReviewRiskCaseCloseCmd();
        cmd.setExpectedCaseVersion(4);
        cmd.setExpectedCoordinationVersion(2);
        cmd.setNote("Attempting governance closure.");

        BizException exception = assertThrows(
                BizException.class, () -> service.close(CASE_ID, cmd, OPERATOR_UID));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(riskCaseMapper, never()).updateCase(
                anyLong(), anyInt(), anyString(), any());
        verify(riskCaseMapper, never()).insertEvent(
                anyLong(), anyLong(), anyLong(), anyLong(), anyString(), any(), anyString(),
                any(), anyInt(), anyInt(), anyString());
        verify(adminAuditService, never()).recordRequired(
                anyLong(), anyString(), anyString(), any(), any(), any(), anyString());
    }

    private void stubDetail(ChannelQualityReviewRiskCaseRow riskCase) {
        when(riskCaseMapper.selectById(CASE_ID)).thenReturn(riskCase);
        when(batchMapper.selectCoordinationById(BATCH_ID))
                .thenReturn(batch(2, NOW.plusSeconds(3 * 24 * 60 * 60L)));
        when(batchMapper.selectById(BATCH_ID)).thenReturn(batchSummary());
        when(batchMapper.listTaskStatusCounts(List.of(BATCH_ID))).thenReturn(List.of(
                taskStatus("OPEN", 1),
                taskStatus("COMPLETED", 1)));
    }

    private static ChannelQualityReviewRiskCaseCreateCmd createCmd(
            int expectedCoordinationVersion, long riskEventId) {
        ChannelQualityReviewRiskCaseCreateCmd cmd = new ChannelQualityReviewRiskCaseCreateCmd();
        cmd.setExpectedCoordinationVersion(expectedCoordinationVersion);
        cmd.setRiskEventId(riskEventId);
        cmd.setNote("Risk requires coordination.");
        return cmd;
    }

    private static ChannelQualityReviewBatchCoordinationRow batch(
            int coordinationVersion, Instant effectiveDueAt) {
        ChannelQualityReviewBatchCoordinationRow row = new ChannelQualityReviewBatchCoordinationRow();
        row.setId(BATCH_ID);
        row.setDomain(1);
        row.setSourceType("CHANNEL_HEALTH");
        row.setName("Channel health batch");
        row.setAssigneeUid(11L);
        row.setDueAt(LocalDateTime.ofInstant(effectiveDueAt, ZoneOffset.UTC));
        row.setEffectiveDueAt(LocalDateTime.ofInstant(effectiveDueAt, ZoneOffset.UTC));
        row.setCoordinationVersion(coordinationVersion);
        row.setCandidateCount(2);
        row.setCreateTime(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        return row;
    }

    private static ChannelQualityReviewBatchRow batchSummary() {
        ChannelQualityReviewBatchRow row = new ChannelQualityReviewBatchRow();
        row.setId(BATCH_ID);
        row.setDomain(1);
        row.setSourceType("CHANNEL_HEALTH");
        row.setName("Channel health batch");
        row.setPriority("HIGH");
        row.setDueAt(LocalDateTime.ofInstant(NOW.plusSeconds(3 * 24 * 60 * 60L), ZoneOffset.UTC));
        row.setCandidateCount(2);
        return row;
    }

    private static ChannelQualityReviewRiskCaseRiskNoteEventRow riskNote(
            long eventId, long batchId, int coordinationVersion) {
        ChannelQualityReviewRiskCaseRiskNoteEventRow row =
                new ChannelQualityReviewRiskCaseRiskNoteEventRow();
        row.setId(eventId);
        row.setBatchId(batchId);
        row.setEventType("RISK_NOTE_ADDED");
        row.setRiskCode("BLOCKER");
        row.setCoordinationVersion(coordinationVersion);
        return row;
    }

    private static ChannelQualityReviewRiskCaseRow openRiskCase(
            long caseId,
            String triggerType,
            String status,
            Long ownerUid,
            int caseVersion,
            int openedCoordinationVersion) {
        ChannelQualityReviewRiskCaseRow row = new ChannelQualityReviewRiskCaseRow();
        row.setId(caseId);
        row.setBatchId(BATCH_ID);
        row.setDomain(1);
        row.setTriggerType(triggerType);
        if ("RISK_EVENT".equals(triggerType)) {
            row.setRiskEventId(RISK_EVENT_ID);
            row.setRiskCode("BLOCKER");
        } else {
            row.setDueState("OVERDUE");
        }
        row.setStatus(status);
        row.setOwnerUid(ownerUid);
        row.setCaseVersion(caseVersion);
        row.setOpenedCoordinationVersion(openedCoordinationVersion);
        row.setCreatedByUid(OPERATOR_UID);
        row.setLegacyClosedWithoutSnapshot("CLOSED".equals(status) ? 1 : 0);
        row.setActiveBatchId("CLOSED".equals(status) ? null : BATCH_ID);
        row.setCreateTime(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        row.setUpdateTime(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        return row;
    }

    private static ChannelQualityReviewBatchTaskStatusRow taskStatus(String status, long count) {
        ChannelQualityReviewBatchTaskStatusRow row = new ChannelQualityReviewBatchTaskStatusRow();
        row.setBatchId(BATCH_ID);
        row.setStatus(status);
        row.setTaskCount(count);
        return row;
    }
}
