package com.offerlab.community.post.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.incentive.api.quota.BenefitCodes;
import com.offerlab.community.incentive.api.quota.EntitlementQuotaFacade;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.post.api.dto.ContentAssistEnhancedExceptionDTO;
import com.offerlab.community.post.api.dto.ContentAssistEnhancedReconcileCmd;
import com.offerlab.community.post.api.dto.ContentAssistEnhancedReconcileResultDTO;
import com.offerlab.community.post.api.dto.ContentAssistEnhancedRequestSummaryDTO;
import com.offerlab.community.post.application.ContentAssistEnhancedOperationsService;
import com.offerlab.community.post.infrastructure.persistence.mapper.ContentAssistEnhancedOperationsMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.ContentAssistEnhancedRequestMapper;
import com.offerlab.community.post.infrastructure.persistence.po.ContentAssistEnhancedExceptionRow;
import com.offerlab.community.post.infrastructure.persistence.po.ContentAssistEnhancedReconcileRequestPO;
import com.offerlab.community.post.infrastructure.persistence.po.ContentAssistEnhancedRequestPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentAssistEnhancedRecoveryTest {

    @Mock
    private ContentAssistAiClient aiClient;
    @Mock
    private ContentAssistService contentAssistService;
    @Mock
    private EntitlementQuotaFacade entitlementQuotaFacade;
    @Mock
    private ContentAssistEnhancedRequestMapper requestMapper;
    @Mock
    private ContentAssistEnhancedFinalizer finalizer;
    @Mock
    private SnowflakeIdGenerator idGenerator;
    @Mock
    private ContentAssistEnhancedOperationsMapper operationsMapper;
    @Mock
    private AdminPermissionService adminPermissionService;
    @Mock
    private AdminAuditService adminAuditService;
    @Mock
    private ContentAssistEnhancedService enhancedService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ContentAssistEnhancedService recoveryService;
    private ContentAssistEnhancedOperationsService operationsService;

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        recoveryService = new ContentAssistEnhancedService(
                objectMapper,
                aiClient,
                contentAssistService,
                entitlementQuotaFacade,
                requestMapper,
                finalizer,
                idGenerator);
        operationsService = new ContentAssistEnhancedOperationsService(
                operationsMapper,
                requestMapper,
                enhancedService,
                adminPermissionService,
                adminAuditService,
                idGenerator,
                objectMapper);
        setField(operationsService, "reconcileEnabled", true);
        setField(operationsService, "requestTimeoutSeconds", 120L);
    }

    @Test
    void recentSummariesExposeNoIdempotencyKeyOrContentHash() throws Exception {
        ContentAssistEnhancedRequestPO request = request(101L, 7L, 501L, "SUCCEEDED");
        request.setIdempotencyKey("content-assist:private-key");
        request.setContentHash("c".repeat(64));
        request.setRequestFingerprint("f".repeat(64));
        when(requestMapper.selectRecentByUser(7L, BenefitCodes.CONTENT_ASSIST_ENHANCED, 10))
                .thenReturn(List.of(request));

        List<ContentAssistEnhancedRequestSummaryDTO> summaries = recoveryService.recent(7L, 99);
        String payload = objectMapper.writeValueAsString(summaries);

        assertEquals(1, summaries.size());
        assertEquals(101L, summaries.get(0).getRequestId());
        assertFalse(payload.contains("idempotencyKey"));
        assertFalse(payload.contains("contentHash"));
        assertFalse(payload.contains("content-assist:private-key"));
        assertFalse(payload.contains("c".repeat(64)));
    }

    @Test
    void requestIdStatusUsesCallerUidForIsolation() {
        when(requestMapper.selectByIdAndUid(101L, 7L, BenefitCodes.CONTENT_ASSIST_ENHANCED))
                .thenReturn(null);

        BizException error = assertThrows(BizException.class,
                () -> recoveryService.statusByRequestId(7L, 101L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), error.getCode());
        verify(requestMapper).selectByIdAndUid(101L, 7L, BenefitCodes.CONTENT_ASSIST_ENHANCED);
    }

    @Test
    void onlyValidStaleRunningReservedRowsAreRecovered() {
        ContentAssistEnhancedExceptionRow recoverable = exceptionRow(
                101L, 7L, 501L, 7L, "RUNNING", "RESERVED", "101");
        ContentAssistEnhancedExceptionRow terminal = exceptionRow(
                102L, 8L, 502L, 8L, "SUCCEEDED", "RELEASED", "102");
        ContentAssistEnhancedExceptionRow sourceMismatch = exceptionRow(
                103L, 9L, 503L, 9L, "RUNNING", "RESERVED", "other-request");
        ContentAssistEnhancedRequestPO recoverableRequest = request(101L, 7L, 501L, "RUNNING");
        prepareReconcile(List.of(recoverable, terminal, sourceMismatch));
        when(requestMapper.selectByIdAndUid(101L, 7L, BenefitCodes.CONTENT_ASSIST_ENHANCED))
                .thenReturn(recoverableRequest);
        when(enhancedService.recoverStaleRequest(recoverableRequest, 120L)).thenReturn(true);

        ContentAssistEnhancedReconcileResultDTO result = operationsService.reconcile(command(false, "valid recovery"), 99L);

        assertEquals(3, result.getScanned());
        assertEquals(1, result.getEligible());
        assertEquals(1, result.getRecovered());
        assertEquals(2, result.getSkipped());
        assertTrue(result.getIssues().stream()
                .anyMatch(issue -> "TERMINAL_USAGE_MISMATCH".equals(issue.getIssueType())
                        && Boolean.FALSE.equals(issue.getRecoverable())));
        assertTrue(result.getIssues().stream()
                .anyMatch(issue -> "SOURCE_REFERENCE_MISMATCH".equals(issue.getIssueType())
                        && Boolean.FALSE.equals(issue.getRecoverable())));
        verify(enhancedService).recoverStaleRequest(recoverableRequest, 120L);
        verify(requestMapper).selectByIdAndUid(101L, 7L, BenefitCodes.CONTENT_ASSIST_ENHANCED);
        verify(requestMapper, never()).selectByIdAndUid(102L, 8L, BenefitCodes.CONTENT_ASSIST_ENHANCED);
        verify(requestMapper, never()).selectByIdAndUid(103L, 9L, BenefitCodes.CONTENT_ASSIST_ENHANCED);
    }

    @Test
    void dryRunListsRecoverableRowsWithoutTriggeringRecovery() {
        ContentAssistEnhancedExceptionRow recoverable = exceptionRow(
                101L, 7L, 501L, 7L, "RUNNING", "RESERVED", "101");
        prepareReconcile(List.of(recoverable));
        when(requestMapper.selectByIdAndUid(101L, 7L, BenefitCodes.CONTENT_ASSIST_ENHANCED))
                .thenReturn(request(101L, 7L, 501L, "RUNNING"));

        ContentAssistEnhancedReconcileResultDTO result = operationsService.reconcile(command(true, "preview recovery"), 99L);

        assertTrue(result.getDryRun());
        assertEquals(1, result.getEligible());
        assertEquals(0, result.getRecovered());
        verify(enhancedService, never()).recoverStaleRequest(any(), anyLong());
    }

    @Test
    void completedOperationsRequestReplaysOnlyWhenParametersMatch() throws Exception {
        ContentAssistEnhancedReconcileCmd command = command(false, "replay recovery");
        ContentAssistEnhancedReconcileResultDTO storedResult = ContentAssistEnhancedReconcileResultDTO.builder()
                .dryRun(false)
                .scanned(1)
                .eligible(1)
                .recovered(1)
                .skipped(0)
                .replayed(false)
                .issues(List.of(ContentAssistEnhancedExceptionDTO.builder()
                        .requestId(101L)
                        .issueType("STALE_RUNNING_RESERVED")
                        .recoverable(true)
                        .build()))
                .build();
        ContentAssistEnhancedReconcileRequestPO existing = new ContentAssistEnhancedReconcileRequestPO();
        existing.setOperatorUid(99L);
        existing.setRequestStatus("COMPLETED");
        existing.setRequestFingerprint(ContentAssistSafety.sha256Hex("false|20|replay recovery"));
        existing.setResultJson(objectMapper.writeValueAsString(storedResult));
        when(operationsMapper.selectByOperatorAndKey(99L, command.getIdempotencyKey())).thenReturn(existing);

        ContentAssistEnhancedReconcileResultDTO replayed = operationsService.reconcile(command, 99L);

        assertTrue(replayed.getReplayed());
        assertEquals(1, replayed.getRecovered());
        BizException conflict = assertThrows(BizException.class,
                () -> operationsService.reconcile(command(false, "changed reason"), 99L));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), conflict.getCode());
        assertEquals("AI_ASSIST_RECONCILE_IDEMPOTENCY_CONFLICT", conflict.getMessage());
        verify(enhancedService, never()).recoverStaleRequest(any(), anyLong());
    }

    private void prepareReconcile(List<ContentAssistEnhancedExceptionRow> rows) {
        when(operationsMapper.selectByOperatorAndKey(99L, "ops-reconcile-key-0001")).thenReturn(null);
        when(idGenerator.nextId()).thenReturn(900L);
        when(enhancedService.effectiveRecoveryTimeoutSeconds(120L)).thenReturn(120L);
        when(operationsMapper.selectExceptions(120L, 20)).thenReturn(rows);
        when(operationsMapper.complete(eq(900L), any())).thenReturn(1);
    }

    private static ContentAssistEnhancedReconcileCmd command(boolean dryRun, String reason) {
        return ContentAssistEnhancedReconcileCmd.builder()
                .dryRun(dryRun)
                .limit(20)
                .idempotencyKey("ops-reconcile-key-0001")
                .reason(reason)
                .build();
    }

    private static ContentAssistEnhancedRequestPO request(Long id, Long uid, Long usageId, String status) {
        ContentAssistEnhancedRequestPO request = new ContentAssistEnhancedRequestPO();
        request.setId(id);
        request.setUid(uid);
        request.setUsageId(usageId);
        request.setRequestStatus(status);
        request.setConsumerCode(BenefitCodes.CONTENT_ASSIST_ENHANCED);
        return request;
    }

    private static ContentAssistEnhancedExceptionRow exceptionRow(
            Long requestId,
            Long requestUid,
            Long usageId,
            Long usageUid,
            String requestStatus,
            String usageStatus,
            String sourceRef) {
        ContentAssistEnhancedExceptionRow row = new ContentAssistEnhancedExceptionRow();
        row.setRequestId(requestId);
        row.setRequestUid(requestUid);
        row.setUsageId(usageId);
        row.setUsageUid(usageUid);
        row.setRequestStatus(requestStatus);
        row.setUsageStatus(usageStatus);
        row.setUsageSourceRef(sourceRef);
        row.setRequestFingerprint("f".repeat(64));
        row.setAgeSeconds(121L);
        return row;
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
