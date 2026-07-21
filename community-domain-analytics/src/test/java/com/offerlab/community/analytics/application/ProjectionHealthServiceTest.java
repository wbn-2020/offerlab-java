package com.offerlab.community.analytics.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.analytics.api.dto.ProjectionHealthDTO;
import com.offerlab.community.analytics.api.dto.ProjectionReconcileCmd;
import com.offerlab.community.analytics.api.dto.ProjectionReconcileResultDTO;
import com.offerlab.community.analytics.infrastructure.persistence.ProjectionHealthRows.AuditReplayRow;
import com.offerlab.community.analytics.infrastructure.persistence.ProjectionHealthRows.IssueRow;
import com.offerlab.community.analytics.infrastructure.persistence.ProjectionHealthRows.DeliveryHealthRow;
import com.offerlab.community.analytics.infrastructure.persistence.ProjectionHealthRows.KnowledgeLifecycleSourceHealthRow;
import com.offerlab.community.analytics.infrastructure.persistence.ProjectionHealthRows.ReconciliationRunRow;
import com.offerlab.community.analytics.infrastructure.persistence.ProjectionHealthRows.RewardInboxDeliveryHealthRow;
import com.offerlab.community.analytics.infrastructure.persistence.mapper.ProjectionHealthMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.incentive.api.IncentiveDtos.ReconciliationDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.RewardInboxReconcileDTO;
import com.offerlab.community.incentive.api.IncentiveProjectionReconciliationFacade;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.security.AdminPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectionHealthServiceTest {

    @Mock
    private ProjectionHealthMapper mapper;
    @Mock
    private IncentiveProjectionReconciliationFacade incentiveFacade;
    @Mock
    private AdminPermissionService permissions;
    @Mock
    private AdminAuditService audit;

    private ProjectionHealthService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new ProjectionHealthService(
                mapper, incentiveFacade, permissions, audit, objectMapper,
                new SnowflakeIdGenerator(1, 1));
        org.mockito.Mockito.lenient().when(mapper.selectExistingProjectionTables()).thenReturn(List.of(
                "t_admin_audit_log",
                "t_projection_reconcile_request",
                "t_incentive_account",
                "t_incentive_ledger",
                "t_incentive_reconciliation_run",
                "t_incentive_reconciliation_item",
                "t_incentive_reward_inbox",
                "t_community_role_grant",
                "t_community_role_definition",
                "t_incentive_freeze_record",
                "t_collab_content_maintenance_task",
                "t_collab_content_need",
                "t_collab_content_need_event",
                "t_notif_retry_task",
                "t_search_index_retry_task",
                "t_feed_feedback_preference",
                "t_collab_topic_post",
                "t_post_main",
                "t_collab_series",
                "t_collab_series_submission",
                "t_outbox_message",
                "t_int_content_suggestion",
                "t_post_reference",
                "t_post_knowledge_relation",
                "t_int_post_outcome"
        ));
        org.mockito.Mockito.doNothing().when(permissions)
                .requireScope(eq(8L), eq(AdminPermissionService.ROLE_OPS));
        org.mockito.Mockito.lenient().when(mapper.reserveReconciliationRequest(
                any(), any(), any(), any(), any(), any())).thenReturn(1);
        org.mockito.Mockito.lenient().when(mapper.completeReconciliationRequest(
                any(), any())).thenReturn(1);
    }

    @Test
    void summaryReportsMissingProjectionDependenciesWithoutQueryingMissingTables() {
        when(mapper.selectExistingProjectionTables()).thenReturn(List.of("t_admin_audit_log"));

        List<ProjectionHealthDTO> result = service.summary(8L);

        ProjectionHealthDTO feed = result.stream()
                .filter(item -> "FEED_FEEDBACK_VISIBILITY".equals(item.getProjectionType()))
                .findFirst()
                .orElseThrow();
        assertEquals("UNAVAILABLE", feed.getHealthStatus());
        assertFalse(feed.getAvailable());
        verify(mapper, never()).countFeedFeedbackVisibilityIssues(any(Integer.class));
    }

    @Test
    void summaryExposesOutboxWatermarkBacklogAndTerminalTimes() {
        LocalDateTime now = LocalDateTime.now();
        DeliveryHealthRow row = new DeliveryHealthRow();
        row.setWatermark(91L);
        row.setBacklogCount(3L);
        row.setFailedCount(1L);
        row.setOldestBacklogAt(now.minusMinutes(10));
        row.setLastSuccessAt(now.minusMinutes(2));
        row.setLastFailureAt(now.minusMinutes(1));
        when(mapper.selectOutboxDeliveryHealth()).thenReturn(row);
        when(mapper.countOutboxDeliveryIssues(1001)).thenReturn(1L);

        ProjectionHealthDTO outbox = service.summary(8L).stream()
                .filter(item -> "OUTBOX_DELIVERY".equals(item.getProjectionType()))
                .findFirst()
                .orElseThrow();

        assertEquals("ATTENTION", outbox.getHealthStatus());
        assertEquals(91L, outbox.getWatermark());
        assertEquals(3L, outbox.getBacklogCount());
        assertTrue(outbox.getBacklogAgeSeconds() >= 600);
        assertEquals("EXISTING_OUTBOX_OPS", outbox.getRepairMode());
        assertFalse(outbox.getReconciliationSupported());
    }

    @Test
    void summaryExposesRewardInboxPendingOverSixtyMinuteSla() {
        LocalDateTime now = LocalDateTime.now();
        RewardInboxDeliveryHealthRow row = new RewardInboxDeliveryHealthRow();
        row.setWatermark(301L);
        row.setBacklogCount(7L);
        row.setOldestBacklogAt(now.minusMinutes(90));
        row.setLastSuccessAt(now.minusMinutes(3));
        row.setLastFailureAt(now.minusMinutes(2));
        when(mapper.selectRewardInboxDeliveryHealth()).thenReturn(row);
        when(mapper.countOverdueRewardInboxDeliveryIssues(
                any(LocalDateTime.class), eq(1001))).thenReturn(4L);

        ProjectionHealthDTO rewardInbox = service.summary(8L).stream()
                .filter(item -> "REWARD_INBOX_DELIVERY".equals(item.getProjectionType()))
                .findFirst()
                .orElseThrow();

        assertEquals("ATTENTION", rewardInbox.getHealthStatus());
        assertEquals(60, rewardInbox.getSlaMinutes());
        assertEquals(7L, rewardInbox.getBacklogCount());
        assertEquals(4L, rewardInbox.getIssueCount());
        assertEquals(4L, rewardInbox.getOverdueCount());
        assertTrue(rewardInbox.getBacklogAgeSeconds() >= 5400);
        assertEquals("BOUNDED_EXISTING_INBOX_PROCESSING", rewardInbox.getRepairMode());
        assertTrue(rewardInbox.getReconciliationSupported());

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).countOverdueRewardInboxDeliveryIssues(cutoff.capture(), eq(1001));
        assertFalse(cutoff.getValue().isBefore(now.minusMinutes(60).minusSeconds(1)));
        assertFalse(cutoff.getValue().isAfter(now.minusMinutes(60).plusSeconds(1)));
    }

    @Test
    void incentiveAccountHealthDeclaresDiagnosticScanInsteadOfRepair() {
        ReconciliationRunRow row = new ReconciliationRunRow();
        row.setRunId(17L);
        row.setIssueCount(2L);
        when(mapper.selectLatestIncentiveReconciliation()).thenReturn(row);

        ProjectionHealthDTO account = service.summary(8L).stream()
                .filter(item -> "INCENTIVE_ACCOUNT".equals(item.getProjectionType()))
                .findFirst()
                .orElseThrow();

        assertEquals("DIAGNOSTIC_SCAN_AND_DIFFERENCE_RECORDING", account.getRepairMode());
        assertEquals("ATTENTION", account.getHealthStatus());
        assertEquals(2L, account.getIssueCount());
    }

    @Test
    void knowledgeLifecycleSummaryExposesBoundedCountsAndOldestAges() {
        LocalDateTime now = LocalDateTime.now();
        when(mapper.selectPendingSuggestionHealth(1001))
                .thenReturn(knowledgeHealth(2, now.minusHours(2)));
        when(mapper.selectBrokenReferenceHealth(1001))
                .thenReturn(knowledgeHealth(3, now.minusHours(4)));
        when(mapper.selectPendingKnowledgeRelationHealth(1001))
                .thenReturn(knowledgeHealth(4, now.minusHours(3)));
        when(mapper.selectInvalidPublicRelationTargetHealth(1001))
                .thenReturn(knowledgeHealth(5, now.minusHours(5)));
        when(mapper.selectDueOutcomeRevisitHealth(1001))
                .thenReturn(knowledgeHealth(6, now.minusHours(6)));

        ProjectionHealthDTO knowledge = service.summary(8L).stream()
                .filter(item -> "KNOWLEDGE_LIFECYCLE".equals(item.getProjectionType()))
                .findFirst()
                .orElseThrow();

        assertEquals("ATTENTION", knowledge.getHealthStatus());
        assertTrue(knowledge.getAvailable());
        assertFalse(knowledge.getReconciliationSupported());
        assertEquals("DIAGNOSIS_ONLY", knowledge.getRepairMode());
        assertEquals(20L, knowledge.getIssueCount());
        assertEquals(6L, knowledge.getBacklogCount());
        assertEquals(6L, knowledge.getOverdueCount());
        assertTrue(knowledge.getBacklogAgeSeconds() >= 6 * 60 * 60);
        assertTrue(knowledge.getAttentionReasons().stream()
                .anyMatch(reason -> reason.startsWith("PENDING_SUGGESTIONS count=2 ")
                        && reason.contains("oldestAgeSeconds=")));
        assertTrue(knowledge.getAttentionReasons().stream()
                .anyMatch(reason -> reason.startsWith("INVALID_PUBLIC_RELATION_TARGETS count=5 ")));
    }

    @Test
    void knowledgeLifecycleSummaryDegradesOnlyTheMissingSourceTable() {
        when(mapper.selectExistingProjectionTables()).thenReturn(List.of(
                "t_int_content_suggestion",
                "t_post_knowledge_relation",
                "t_post_main",
                "t_int_post_outcome"
        ));
        when(mapper.selectPendingSuggestionHealth(1001))
                .thenReturn(knowledgeHealth(1, LocalDateTime.now().minusHours(1)));

        ProjectionHealthDTO knowledge = service.summary(8L).stream()
                .filter(item -> "KNOWLEDGE_LIFECYCLE".equals(item.getProjectionType()))
                .findFirst()
                .orElseThrow();

        assertEquals("ATTENTION", knowledge.getHealthStatus());
        assertTrue(knowledge.getAvailable());
        assertEquals(1L, knowledge.getIssueCount());
        assertTrue(knowledge.getAttentionReasons().contains(
                "SOURCE_ERROR BROKEN_REFERENCES=TABLE_MISSING:t_post_reference"));
        verify(mapper, never()).selectBrokenReferenceHealth(any(Integer.class));
    }

    @Test
    void knowledgeLifecycleSummaryReportsSourceReadErrorsWithoutFailingOtherSources() {
        when(mapper.selectBrokenReferenceHealth(1001))
                .thenThrow(new IllegalStateException("source unavailable"));
        when(mapper.selectDueOutcomeRevisitHealth(1001))
                .thenReturn(knowledgeHealth(2, LocalDateTime.now().minusMinutes(30)));

        ProjectionHealthDTO knowledge = service.summary(8L).stream()
                .filter(item -> "KNOWLEDGE_LIFECYCLE".equals(item.getProjectionType()))
                .findFirst()
                .orElseThrow();

        assertEquals("ATTENTION", knowledge.getHealthStatus());
        assertEquals(2L, knowledge.getIssueCount());
        assertTrue(knowledge.getAttentionReasons().contains(
                "SOURCE_ERROR BROKEN_REFERENCES=READ_UNAVAILABLE"));
    }

    @Test
    void knowledgeLifecycleSummaryIsUnavailableWhenEveryLifecycleSourceIsMissing() {
        when(mapper.selectExistingProjectionTables()).thenReturn(List.of("t_admin_audit_log"));

        ProjectionHealthDTO knowledge = service.summary(8L).stream()
                .filter(item -> "KNOWLEDGE_LIFECYCLE".equals(item.getProjectionType()))
                .findFirst()
                .orElseThrow();

        assertEquals("UNAVAILABLE", knowledge.getHealthStatus());
        assertFalse(knowledge.getAvailable());
        assertEquals(0L, knowledge.getIssueCount());
        assertTrue(knowledge.getAttentionReasons().stream()
                .allMatch(reason -> reason.startsWith("SOURCE_ERROR ")));
        verify(mapper, never()).selectPendingSuggestionHealth(any(Integer.class));
        verify(mapper, never()).selectDueOutcomeRevisitHealth(any(Integer.class));
    }

    @Test
    void dryRunDelegatesToPreviewAndWritesAnAuditRecord() {
        ProjectionReconcileCmd cmd = command(true, "account-preview-1");
        when(mapper.selectReconciliationAudit(any(String.class))).thenReturn(null);
        when(incentiveFacade.previewAccountProjection(10, "bounded preview", 8L))
                .thenReturn(ReconciliationDTO.builder()
                        .runId(71L)
                        .scannedCount(10)
                        .mismatchCount(2)
                        .coverageComplete(false)
                        .build());

        ProjectionReconcileResultDTO result = service.reconcile("INCENTIVE_ACCOUNT", cmd, 8L);

        assertEquals("DIAGNOSTIC_PREVIEW_COMPLETED", result.getStatus());
        assertEquals(71L, result.getDelegatedRunId());
        assertEquals(2, result.getIssueCount());
        assertEquals(0, result.getChangedCount());
        assertFalse(result.getReplayed());
        verify(incentiveFacade).previewAccountProjection(10, "bounded preview", 8L);
        verify(incentiveFacade, never()).reconcileAccountProjection(any(), any(), any());
        verify(audit).recordRequired(eq(8L), eq("COMMUNITY_PROJECTION_RECONCILE"),
                eq("COMMUNITY_PROJECTION"), any(), any(), eq(result), eq("bounded preview"));
    }

    @Test
    void incentiveAccountExecutionReportsDiagnosticScanAndNeverClaimsChanges() {
        ProjectionReconcileCmd cmd = command(false, "account-scan");
        when(incentiveFacade.reconcileAccountProjection(10, "repair account projection", 8L))
                .thenReturn(ReconciliationDTO.builder()
                        .runId(72L)
                        .scannedCount(10)
                        .mismatchCount(3)
                        .coverageComplete(false)
                        .build());

        ProjectionReconcileResultDTO result =
                service.reconcile("INCENTIVE_ACCOUNT", cmd, 8L);

        assertEquals("DIAGNOSTIC_SCAN_COMPLETED", result.getStatus());
        assertEquals(10, result.getProcessedCount());
        assertEquals(3, result.getIssueCount());
        assertEquals(0, result.getChangedCount());
        verify(incentiveFacade).reconcileAccountProjection(
                10, "repair account projection", 8L);
    }

    @Test
    void rewardInboxDryRunUsesPreviewOnlyAndReportsBoundedCoverage() {
        ProjectionReconcileCmd cmd = command(true, "reward-preview");
        when(incentiveFacade.previewOverdueRewardInbox(10, 8L)).thenReturn(11);

        ProjectionReconcileResultDTO result =
                service.reconcile("REWARD_INBOX_DELIVERY", cmd, 8L);

        assertEquals("DRY_RUN_COMPLETED", result.getStatus());
        assertEquals(60, result.getSlaMinutes());
        assertEquals(10, result.getProcessedCount());
        assertEquals(10, result.getIssueCount());
        assertEquals(0, result.getChangedCount());
        assertEquals(0, result.getAppliedCount());
        assertEquals(0, result.getRejectedCount());
        assertFalse(result.getCoverageComplete());
        verify(incentiveFacade).previewOverdueRewardInbox(10, 8L);
        verify(incentiveFacade, never()).reconcileOverdueRewardInbox(any(), any(), any());
    }

    @Test
    void rewardInboxReconcileReusesExistingProcessingAndMapsResultExactly() {
        ProjectionReconcileCmd cmd = command(false, "reward-reconcile");
        when(incentiveFacade.reconcileOverdueRewardInbox(
                10, "repair account projection", 8L))
                .thenReturn(RewardInboxReconcileDTO.builder()
                        .slaMinutes(60)
                        .processedCount(5)
                        .appliedCount(4)
                        .rejectedCount(1)
                        .coverageComplete(true)
                        .build());

        ProjectionReconcileResultDTO result =
                service.reconcile("REWARD_INBOX_DELIVERY", cmd, 8L);

        assertEquals("COMPLETED", result.getStatus());
        assertEquals(60, result.getSlaMinutes());
        assertEquals(5, result.getProcessedCount());
        assertEquals(5, result.getIssueCount());
        assertEquals(5, result.getChangedCount());
        assertEquals(4, result.getAppliedCount());
        assertEquals(1, result.getRejectedCount());
        assertTrue(result.getCoverageComplete());
        verify(incentiveFacade).reconcileOverdueRewardInbox(
                10, "repair account projection", 8L);
        verify(incentiveFacade, never()).previewOverdueRewardInbox(any(), any());
    }

    @Test
    void roleGrantRepairKeepsRealRepairCompletionSemantics() {
        ProjectionReconcileCmd cmd = command(false, "role-reconcile");
        when(incentiveFacade.previewExpiredRoleGrants(10, 8L)).thenReturn(3);
        when(incentiveFacade.reconcileExpiredRoleGrants(
                10, "repair account projection", 8L)).thenReturn(3);

        ProjectionReconcileResultDTO result =
                service.reconcile("ROLE_GRANT_EXPIRY", cmd, 8L);

        assertEquals("COMPLETED", result.getStatus());
        assertEquals(3, result.getIssueCount());
        assertEquals(3, result.getChangedCount());
        verify(incentiveFacade).reconcileExpiredRoleGrants(
                10, "repair account projection", 8L);
    }

    @Test
    void repeatedIdempotencyKeyReplaysStoredResult() throws Exception {
        ProjectionReconcileCmd cmd = command(false, "same-key");
        // Let the first call calculate the exact request fingerprint.
        when(mapper.selectReconciliationAudit(any(String.class))).thenReturn(null);
        when(incentiveFacade.reconcileAccountProjection(10, "repair account projection", 8L))
                .thenReturn(ReconciliationDTO.builder()
                        .runId(91L)
                        .scannedCount(10)
                        .mismatchCount(1)
                        .coverageComplete(true)
                        .build());
        ProjectionReconcileResultDTO first =
                service.reconcile("INCENTIVE_ACCOUNT", cmd, 8L);

        AuditReplayRow replay = new AuditReplayRow();
        replay.setOperatorUid(8L);
        replay.setAfterJson(objectMapper.writeValueAsString(first));
        when(mapper.selectReconciliationAudit(any(String.class))).thenReturn(replay);

        ProjectionReconcileResultDTO second =
                service.reconcile("INCENTIVE_ACCOUNT", cmd, 8L);

        assertTrue(second.getReplayed());
        assertEquals(first.getRequestFingerprint(), second.getRequestFingerprint());
        verify(incentiveFacade).reconcileAccountProjection(10, "repair account projection", 8L);
    }

    @Test
    void duplicateReservationReplaysCompletedResultWithoutRunningRepairAgain() throws Exception {
        ProjectionReconcileCmd cmd = command(false, "concurrent-key");
        when(mapper.selectReconciliationAudit(any(String.class))).thenReturn(null);
        when(incentiveFacade.reconcileAccountProjection(10, "repair account projection", 8L))
                .thenReturn(ReconciliationDTO.builder()
                        .runId(101L)
                        .scannedCount(10)
                        .mismatchCount(1)
                        .coverageComplete(true)
                        .build());

        ProjectionReconcileResultDTO stored =
                service.reconcile("INCENTIVE_ACCOUNT", cmd, 8L);

        when(mapper.reserveReconciliationRequest(any(), any(), any(), any(), any(), any()))
                .thenReturn(0);
        com.offerlab.community.analytics.infrastructure.persistence.ProjectionHealthRows.ReconcileRequestRow row =
                new com.offerlab.community.analytics.infrastructure.persistence.ProjectionHealthRows.ReconcileRequestRow();
        row.setOperatorUid(8L);
        row.setProjectionType("INCENTIVE_ACCOUNT");
        row.setRequestFingerprint(stored.getRequestFingerprint());
        row.setRequestStatus("COMPLETED");
        row.setResultJson(objectMapper.writeValueAsString(stored));
        when(mapper.lockReconciliationRequest(any(String.class))).thenReturn(row);

        ProjectionReconcileResultDTO replayed =
                service.reconcile("INCENTIVE_ACCOUNT", cmd, 8L);

        assertTrue(replayed.getReplayed());
        assertEquals(stored.getRequestFingerprint(), replayed.getRequestFingerprint());
        verify(incentiveFacade).reconcileAccountProjection(10, "repair account projection", 8L);
    }

    @Test
    void diagnosisOnlyProjectionCannotBeReconciledThroughGenericEndpoint() {
        ProjectionReconcileCmd cmd = command(false, "diagnose-only");

        BizException error = assertThrows(BizException.class,
                () -> service.reconcile("ROLE_MAINTENANCE_ACCESS", cmd, 8L));

        assertEquals(ErrorCode.INVALID_STATUS.getCode(), error.getCode());
        verify(incentiveFacade, never()).reconcileAccountProjection(any(), any(), any());
        verify(incentiveFacade, never()).reconcileExpiredRoleGrants(any(), any(), any());
    }

    @Test
    void issueListingUsesKeysetCursorAndBoundedPage() {
        IssueRow first = issue(20L);
        IssueRow second = issue(10L);
        when(mapper.listExpiredRoleGrantIssues(0L, 3)).thenReturn(List.of(first, second));

        var result = service.issues("ROLE_GRANT_EXPIRY", 0L, 2, 8L);

        assertEquals(2, result.getItems().size());
        assertFalse(result.getHasMore());
        verify(mapper).listExpiredRoleGrantIssues(0L, 3);
    }

    @Test
    void rewardInboxIssuesUseSlaCutoffAndKeysetPagination() {
        IssueRow first = rewardIssue(30L);
        IssueRow second = rewardIssue(20L);
        IssueRow lookahead = rewardIssue(10L);
        when(mapper.listOverdueRewardInboxDeliveryIssues(
                any(LocalDateTime.class), eq(0L), eq(3)))
                .thenReturn(List.of(first, second, lookahead));

        var result = service.issues("REWARD_INBOX_DELIVERY", 0L, 2, 8L);

        assertEquals(2, result.getItems().size());
        assertTrue(result.getHasMore());
        assertEquals("20", result.getNextCursor());
        assertEquals("REWARD_INBOX_PENDING_OVER_SLA",
                result.getItems().get(0).getIssueType());
        verify(mapper).listOverdueRewardInboxDeliveryIssues(
                any(LocalDateTime.class), eq(0L), eq(3));
    }

    @Test
    void knowledgeLifecycleIssuesMergeAvailableSourcesAndExposeSourceErrors() {
        when(mapper.selectExistingProjectionTables()).thenReturn(List.of(
                "t_int_content_suggestion",
                "t_post_knowledge_relation",
                "t_post_main",
                "t_int_post_outcome"
        ));
        IssueRow suggestion = knowledgeIssue(
                50L, "CONTENT_SUGGESTION_PENDING", "CONTENT_SUGGESTION");
        IssueRow revisit = knowledgeIssue(
                40L, "POST_OUTCOME_REVISIT_DUE", "POST_OUTCOME");
        IssueRow lookahead = knowledgeIssue(
                30L, "KNOWLEDGE_RELATION_PENDING", "POST_KNOWLEDGE_RELATION");
        when(mapper.listPendingSuggestionIssues(0L, 3)).thenReturn(List.of(suggestion));
        when(mapper.listPendingKnowledgeRelationIssues(0L, 3)).thenReturn(List.of(lookahead));
        when(mapper.listDueOutcomeRevisitIssues(0L, 3)).thenReturn(List.of(revisit));

        var result = service.issues("KNOWLEDGE_LIFECYCLE", 0L, 2, 8L);

        assertEquals(2, result.getItems().size());
        assertEquals("50", result.getItems().get(0).getSubjectId());
        assertEquals("40", result.getNextCursor());
        assertTrue(result.getHasMore());
        assertTrue(result.getDegraded());
        assertEquals("KNOWLEDGE_LIFECYCLE_SOURCE_DEGRADED", result.getFallbackReason());
        @SuppressWarnings("unchecked")
        var sourceErrors = (java.util.Map<String, String>) result.getDiagnostics().get("sourceErrors");
        assertEquals("TABLE_MISSING:t_post_reference", sourceErrors.get("BROKEN_REFERENCES"));
        verify(mapper, never()).listBrokenReferenceIssues(any(Long.class), any(Integer.class));
    }

    private static ProjectionReconcileCmd command(boolean dryRun, String key) {
        ProjectionReconcileCmd cmd = new ProjectionReconcileCmd();
        cmd.setDryRun(dryRun);
        cmd.setLimit(10);
        cmd.setReason(dryRun ? "bounded preview" : "repair account projection");
        cmd.setIdempotencyKey(key);
        return cmd;
    }

    private static IssueRow issue(long id) {
        IssueRow row = new IssueRow();
        row.setIssueId(id);
        row.setIssueType("ROLE_GRANT_EXPIRED_NOT_CLOSED");
        row.setSeverity("HIGH");
        row.setSubjectType("COMMUNITY_ROLE_GRANT");
        row.setSubjectId(String.valueOf(id));
        row.setSummary("expired");
        return row;
    }

    private static IssueRow rewardIssue(long id) {
        IssueRow row = new IssueRow();
        row.setIssueId(id);
        row.setIssueType("REWARD_INBOX_PENDING_OVER_SLA");
        row.setSeverity("HIGH");
        row.setSubjectType("INCENTIVE_REWARD_INBOX");
        row.setSubjectId(String.valueOf(id));
        row.setSummary("overdue pending reward");
        return row;
    }

    private static KnowledgeLifecycleSourceHealthRow knowledgeHealth(
            long count,
            LocalDateTime oldestIssueAt) {
        KnowledgeLifecycleSourceHealthRow row = new KnowledgeLifecycleSourceHealthRow();
        row.setIssueCount(count);
        row.setOldestIssueAt(oldestIssueAt);
        return row;
    }

    private static IssueRow knowledgeIssue(long id, String issueType, String subjectType) {
        IssueRow row = new IssueRow();
        row.setIssueId(id);
        row.setIssueType(issueType);
        row.setSeverity("MEDIUM");
        row.setSubjectType(subjectType);
        row.setSubjectId(String.valueOf(id));
        row.setSummary("knowledge lifecycle issue");
        row.setDetectedAt(LocalDateTime.now().minusMinutes(5));
        return row;
    }
}
