package com.offerlab.community.analytics.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.analytics.api.dto.ProjectionHealthDTO;
import com.offerlab.community.analytics.api.dto.ProjectionIssueDTO;
import com.offerlab.community.analytics.api.dto.ProjectionReconcileCmd;
import com.offerlab.community.analytics.api.dto.ProjectionReconcileResultDTO;
import com.offerlab.community.analytics.infrastructure.persistence.ProjectionHealthRows.AuditReplayRow;
import com.offerlab.community.analytics.infrastructure.persistence.ProjectionHealthRows.IssueRow;
import com.offerlab.community.analytics.infrastructure.persistence.ProjectionHealthRows.KnowledgeLifecycleSourceHealthRow;
import com.offerlab.community.analytics.infrastructure.persistence.ProjectionHealthRows.ReconcileRequestRow;
import com.offerlab.community.analytics.infrastructure.persistence.ProjectionHealthRows.ReconciliationRunRow;
import com.offerlab.community.analytics.infrastructure.persistence.ProjectionHealthRows.DeliveryHealthRow;
import com.offerlab.community.analytics.infrastructure.persistence.ProjectionHealthRows.RewardInboxDeliveryHealthRow;
import com.offerlab.community.analytics.infrastructure.persistence.mapper.ProjectionHealthMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.incentive.api.IncentiveDtos.ReconciliationDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.RewardInboxReconcileDTO;
import com.offerlab.community.incentive.api.IncentiveProjectionReconciliationFacade;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.security.AdminPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class ProjectionHealthService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_CURSOR_LENGTH = 256;
    private static final int ISSUE_COUNT_CAP = 1000;
    private static final String AUDIT_ACTION = "COMMUNITY_PROJECTION_RECONCILE";
    private static final String AUDIT_RESOURCE = "COMMUNITY_PROJECTION";
    private static final String KNOWLEDGE_LIFECYCLE = "KNOWLEDGE_LIFECYCLE";
    private static final String PENDING_SUGGESTIONS = "PENDING_SUGGESTIONS";
    private static final String BROKEN_REFERENCES = "BROKEN_REFERENCES";
    private static final String PENDING_RELATIONS = "PENDING_RELATIONS";
    private static final String INVALID_PUBLIC_RELATION_TARGETS = "INVALID_PUBLIC_RELATION_TARGETS";
    private static final String DUE_OUTCOME_REVISITS = "DUE_OUTCOME_REVISITS";
    private static final String FRESHNESS_ATTENTION = "FRESHNESS_ATTENTION";
    private static final List<String> KNOWLEDGE_SOURCE_ORDER = List.of(
            PENDING_SUGGESTIONS,
            BROKEN_REFERENCES,
            PENDING_RELATIONS,
            INVALID_PUBLIC_RELATION_TARGETS,
            DUE_OUTCOME_REVISITS,
            FRESHNESS_ATTENTION);
    private static final int LEGACY_CURSOR_SOURCE_ORDER = Integer.MAX_VALUE;

    private static final Map<String, ProjectionDefinition> DEFINITIONS = definitions();

    private final ProjectionHealthMapper mapper;
    private final IncentiveProjectionReconciliationFacade incentiveReconciliationFacade;
    private final AdminPermissionService adminPermissionService;
    private final AdminAuditService adminAuditService;
    private final ObjectMapper objectMapper;
    private final SnowflakeIdGenerator idGenerator;

    public List<ProjectionHealthDTO> summary(Long operatorUid) {
        requireOperations(operatorUid);
        Set<String> existingTables = Set.copyOf(mapper.selectExistingProjectionTables());
        LocalDateTime checkedAt = LocalDateTime.now();
        return DEFINITIONS.values().stream()
                .map(definition -> health(definition, existingTables, checkedAt))
                .toList();
    }

    public PageResult<ProjectionIssueDTO> issues(
            String rawProjectionType,
            long cursor,
            int requestedSize,
            Long operatorUid) {
        if (cursor < 0) {
            throw invalidCursor();
        }
        return issues(rawProjectionType, cursor == 0 ? "0" : String.valueOf(cursor),
                requestedSize, operatorUid);
    }

    public PageResult<ProjectionIssueDTO> issues(
            String rawProjectionType,
            String rawCursor,
            int requestedSize,
            Long operatorUid) {
        requireOperations(operatorUid);
        ProjectionDefinition definition = requireDefinition(rawProjectionType);
        int size = Math.max(1, Math.min(requestedSize <= 0 ? 20 : requestedSize, MAX_PAGE_SIZE));
        KnowledgeLifecycleCursor knowledgeCursor = null;
        long numericCursor = 0L;
        if (KNOWLEDGE_LIFECYCLE.equals(definition.type())) {
            knowledgeCursor = KnowledgeLifecycleCursor.parse(rawCursor);
        } else {
            numericCursor = parseNumericCursor(rawCursor);
        }
        Set<String> existingTables = Set.copyOf(mapper.selectExistingProjectionTables());
        if (KNOWLEDGE_LIFECYCLE.equals(definition.type())) {
            return knowledgeLifecycleIssues(existingTables, knowledgeCursor, size);
        }
        requireAvailable(definition, existingTables);
        List<IssueRow> rows = issueRows(definition.type(), numericCursor, size + 1);
        boolean hasMore = rows.size() > size;
        List<IssueRow> visible = rows.stream().limit(size).toList();
        String nextCursor = hasMore && !visible.isEmpty()
                ? String.valueOf(visible.get(visible.size() - 1).getIssueId())
                : null;
        List<ProjectionIssueDTO> items = visible.stream()
                .map(row -> toIssue(definition.type(), row))
                .toList();
        return PageResult.of(items, nextCursor, hasMore);
    }

    @Transactional
    public ProjectionReconcileResultDTO reconcile(
            String rawProjectionType,
            ProjectionReconcileCmd cmd,
            Long operatorUid) {
        requireOperations(operatorUid);
        ProjectionDefinition definition = requireDefinition(rawProjectionType);
        if (!definition.reconciliationSupported()) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                    "this projection is diagnosis-only; use its owning domain repair endpoint");
        }
        ReconcileRequest request = requireRequest(cmd);
        requireAvailable(definition, Set.copyOf(mapper.selectExistingProjectionTables()));

        String fingerprint = fingerprint(definition.type(), request);
        String resourceId = resourceId(operatorUid, definition.type(), request.idempotencyKey());
        AuditReplayRow replay = mapper.selectReconciliationAudit(resourceId);
        if (replay != null) {
            ProjectionReconcileResultDTO previous = parseReplay(replay, fingerprint, operatorUid);
            previous.setReplayed(true);
            return previous;
        }

        int reserved = mapper.reserveReconciliationRequest(
                idGenerator.nextId(),
                resourceId,
                operatorUid,
                definition.type(),
                request.idempotencyKey(),
                fingerprint);
        if (reserved != 1) {
            ProjectionReconcileResultDTO previous = parseReservedReplay(
                    mapper.lockReconciliationRequest(resourceId),
                    definition.type(),
                    fingerprint,
                    operatorUid);
            previous.setReplayed(true);
            return previous;
        }

        adminAuditService.requireWritable(AUDIT_ACTION, AUDIT_RESOURCE, resourceId);
        ProjectionReconcileResultDTO result = switch (definition.type()) {
            case "INCENTIVE_ACCOUNT" -> reconcileIncentiveAccount(
                    request, fingerprint, operatorUid);
            case "REWARD_INBOX_DELIVERY" -> reconcileRewardInboxDelivery(
                    request, fingerprint, operatorUid);
            case "ROLE_GRANT_EXPIRY" -> reconcileExpiredRoleGrants(
                    request, fingerprint, operatorUid);
            default -> throw new BizException(ErrorCode.INVALID_STATUS);
        };
        adminAuditService.recordRequired(
                operatorUid,
                AUDIT_ACTION,
                AUDIT_RESOURCE,
                resourceId,
                Map.of(
                        "projectionType", definition.type(),
                        "dryRun", request.dryRun(),
                        "limit", request.limit(),
                        "requestFingerprint", fingerprint
                ),
                result,
                request.reason());
        completeReservation(resourceId, result);
        return result;
    }

    private ProjectionReconcileResultDTO parseReservedReplay(
            ReconcileRequestRow row,
            String projectionType,
            String fingerprint,
            Long operatorUid) {
        if (row == null
                || !operatorUid.equals(row.getOperatorUid())
                || !projectionType.equals(row.getProjectionType())) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                    "projection reconcile reservation is unavailable");
        }
        if (!fingerprint.equals(row.getRequestFingerprint())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "idempotency key was already used with different parameters");
        }
        if (!"COMPLETED".equals(row.getRequestStatus())
                || !StringUtils.hasText(row.getResultJson())) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                    "projection reconcile request is still in progress");
        }
        try {
            return objectMapper.readValue(row.getResultJson(), ProjectionReconcileResultDTO.class);
        } catch (Exception e) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                    "projection reconcile result is unreadable");
        }
    }

    private void completeReservation(String resourceId, ProjectionReconcileResultDTO result) {
        try {
            String resultJson = objectMapper.writeValueAsString(result);
            if (mapper.completeReconciliationRequest(resourceId, resultJson) != 1) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                        "projection reconcile reservation could not be completed");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                    "projection reconcile result could not be serialized");
        }
    }

    private ProjectionHealthDTO health(
            ProjectionDefinition definition,
            Set<String> existingTables,
            LocalDateTime checkedAt) {
        if (KNOWLEDGE_LIFECYCLE.equals(definition.type())) {
            return knowledgeLifecycleHealth(definition, existingTables, checkedAt);
        }
        Set<String> missing = definition.requiredTables().stream()
                .filter(table -> !existingTables.contains(table))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!missing.isEmpty()) {
            return ProjectionHealthDTO.builder()
                    .projectionType(definition.type())
                    .displayName(definition.displayName())
                    .healthStatus("UNAVAILABLE")
                    .issueCount(0L)
                    .issueCountCapped(false)
                    .available(false)
                    .reconciliationSupported(definition.reconciliationSupported())
                    .checkedAt(checkedAt)
                    .attentionReasons(List.of("缺少依赖表：" + String.join(", ", missing)))
                    .build();
        }
        if ("INCENTIVE_ACCOUNT".equals(definition.type())) {
            ReconciliationRunRow latest = mapper.selectLatestIncentiveReconciliation();
            if (latest == null) {
                return ProjectionHealthDTO.builder()
                        .projectionType(definition.type())
                        .displayName(definition.displayName())
                        .healthStatus("UNKNOWN")
                        .issueCount(0L)
                        .issueCountCapped(false)
                        .available(true)
                        .reconciliationSupported(true)
                        .repairMode("DIAGNOSTIC_SCAN_AND_DIFFERENCE_RECORDING")
                        .checkedAt(checkedAt)
                        .attentionReasons(List.of("尚无激励账户对账批次"))
                        .build();
            }
            long issues = Math.max(0, latest.getIssueCount() == null ? 0 : latest.getIssueCount());
            return ProjectionHealthDTO.builder()
                    .projectionType(definition.type())
                    .displayName(definition.displayName())
                    .healthStatus(issues == 0 ? "STABLE" : "ATTENTION")
                    .issueCount(issues)
                    .issueCountCapped(false)
                    .available(true)
                    .reconciliationSupported(true)
                    .repairMode("DIAGNOSTIC_SCAN_AND_DIFFERENCE_RECORDING")
                    .latestRunId(latest.getRunId())
                    .checkedAt(checkedAt)
                    .attentionReasons(issues == 0 ? List.of() : List.of(definition.attentionReason()))
                    .build();
        }
        if ("OUTBOX_DELIVERY".equals(definition.type())) {
            return outboxDeliveryHealth(definition, checkedAt);
        }
        if ("REWARD_INBOX_DELIVERY".equals(definition.type())) {
            return rewardInboxDeliveryHealth(definition, checkedAt);
        }
        long boundedCount = issueCount(definition.type(), ISSUE_COUNT_CAP + 1);
        boolean capped = boundedCount > ISSUE_COUNT_CAP;
        long visibleCount = Math.min(boundedCount, ISSUE_COUNT_CAP);
        return ProjectionHealthDTO.builder()
                .projectionType(definition.type())
                .displayName(definition.displayName())
                .healthStatus(visibleCount == 0 ? "STABLE" : "ATTENTION")
                .issueCount(visibleCount)
                .issueCountCapped(capped)
                .available(true)
                .reconciliationSupported(definition.reconciliationSupported())
                .repairMode(definition.reconciliationSupported() ? "BOUNDED_MANUAL" : "DIAGNOSIS_ONLY")
                .checkedAt(checkedAt)
                .attentionReasons(visibleCount == 0 ? List.of() : List.of(definition.attentionReason()))
                .build();
    }

    private long issueCount(String projectionType, int cap) {
        return switch (projectionType) {
            case "OUTBOX_DELIVERY" -> mapper.countOutboxDeliveryIssues(cap);
            case "REWARD_INBOX_DELIVERY" -> mapper.countOverdueRewardInboxDeliveryIssues(
                    rewardInboxCutoff(LocalDateTime.now()), cap);
            case "ROLE_GRANT_EXPIRY" -> mapper.countExpiredRoleGrants(cap);
            case "ROLE_MAINTENANCE_ACCESS" -> mapper.countInvalidMaintenanceAssignments(cap);
            case "PUBLIC_CONTRIBUTION_VISIBILITY" -> mapper.countNonPublicMaintenanceContributions(cap);
            case "COLLABORATION_TIMELINE" -> mapper.countMissingCollaborationTimelineFacts(cap);
            case "NOTIFICATION_DELIVERY" -> mapper.countNotificationDeliveryIssues(cap);
            case "SEARCH_INDEX_QUEUE" -> mapper.countSearchIndexQueueIssues(cap);
            case "FEED_FEEDBACK_VISIBILITY" -> mapper.countFeedFeedbackVisibilityIssues(cap);
            case "TOPIC_SPACE_VISIBILITY" -> mapper.countTopicSpaceVisibilityIssues(cap);
            default -> 0;
        };
    }

    private List<IssueRow> issueRows(String projectionType, long cursor, int limit) {
        return switch (projectionType) {
            case "OUTBOX_DELIVERY" -> mapper.listOutboxDeliveryIssues(cursor, limit);
            case "REWARD_INBOX_DELIVERY" -> mapper.listOverdueRewardInboxDeliveryIssues(
                    rewardInboxCutoff(LocalDateTime.now()), cursor, limit);
            case "INCENTIVE_ACCOUNT" -> mapper.listIncentiveAccountIssues(cursor, limit);
            case "ROLE_GRANT_EXPIRY" -> mapper.listExpiredRoleGrantIssues(cursor, limit);
            case "ROLE_MAINTENANCE_ACCESS" -> mapper.listInvalidMaintenanceAssignments(cursor, limit);
            case "PUBLIC_CONTRIBUTION_VISIBILITY" ->
                    mapper.listNonPublicMaintenanceContributions(cursor, limit);
            case "COLLABORATION_TIMELINE" ->
                    mapper.listMissingCollaborationTimelineFacts(cursor, limit);
            case "NOTIFICATION_DELIVERY" -> mapper.listNotificationDeliveryIssues(cursor, limit);
            case "SEARCH_INDEX_QUEUE" -> mapper.listSearchIndexQueueIssues(cursor, limit);
            case "FEED_FEEDBACK_VISIBILITY" ->
                    mapper.listFeedFeedbackVisibilityIssues(cursor, limit);
            case "TOPIC_SPACE_VISIBILITY" -> mapper.listTopicSpaceVisibilityIssues(cursor, limit);
            default -> List.of();
        };
    }

    private ProjectionHealthDTO knowledgeLifecycleHealth(
            ProjectionDefinition definition,
            Set<String> existingTables,
            LocalDateTime checkedAt) {
        List<KnowledgeLifecycleSource> sources = List.of(
                readKnowledgeLifecycleSource(
                        PENDING_SUGGESTIONS,
                        Set.of("t_int_content_suggestion"),
                        existingTables,
                        () -> mapper.selectPendingSuggestionHealth(ISSUE_COUNT_CAP + 1)),
                readKnowledgeLifecycleSource(
                        BROKEN_REFERENCES,
                        Set.of("t_post_reference"),
                        existingTables,
                        () -> mapper.selectBrokenReferenceHealth(ISSUE_COUNT_CAP + 1)),
                readKnowledgeLifecycleSource(
                        PENDING_RELATIONS,
                        Set.of("t_post_knowledge_relation"),
                        existingTables,
                        () -> mapper.selectPendingKnowledgeRelationHealth(ISSUE_COUNT_CAP + 1)),
                readKnowledgeLifecycleSource(
                        INVALID_PUBLIC_RELATION_TARGETS,
                        Set.of("t_post_knowledge_relation", "t_post_main"),
                        existingTables,
                        () -> mapper.selectInvalidPublicRelationTargetHealth(ISSUE_COUNT_CAP + 1)),
                readKnowledgeLifecycleSource(
                        DUE_OUTCOME_REVISITS,
                        Set.of("t_int_post_outcome"),
                        existingTables,
                        () -> mapper.selectDueOutcomeRevisitHealth(ISSUE_COUNT_CAP + 1)),
                readKnowledgeLifecycleSource(
                        FRESHNESS_ATTENTION,
                        Set.of("t_int_post_trust_state", "t_post_main"),
                        existingTables,
                        () -> mapper.selectFreshnessAttentionHealth(ISSUE_COUNT_CAP + 1))
        );
        long boundedCount = boundedKnowledgeCount(sources);
        long visibleCount = Math.min(boundedCount, ISSUE_COUNT_CAP);
        long backlogCount = boundedKnowledgeCount(sources.stream()
                .filter(source -> PENDING_SUGGESTIONS.equals(source.source())
                        || PENDING_RELATIONS.equals(source.source()))
                .toList());
        long dueOutcomeRevisitCount = sources.stream()
                .filter(source -> DUE_OUTCOME_REVISITS.equals(source.source()))
                .mapToLong(KnowledgeLifecycleSource::count)
                .findFirst()
                .orElse(0);
        // Freshness age is source-level context only; keep the V12 backlog-age source set.
        LocalDateTime oldestBacklogAt = sources.stream()
                .filter(source -> !FRESHNESS_ATTENTION.equals(source.source()))
                .filter(KnowledgeLifecycleSource::available)
                .filter(source -> source.count() > 0)
                .map(KnowledgeLifecycleSource::oldestIssueAt)
                .filter(java.util.Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);
        long backlogAgeSeconds = oldestBacklogAt == null
                ? 0
                : Math.max(0, Duration.between(oldestBacklogAt, checkedAt).getSeconds());
        long availableSources = sources.stream()
                .filter(KnowledgeLifecycleSource::available)
                .count();
        boolean degraded = availableSources < sources.size();
        List<String> attentionReasons = knowledgeLifecycleAttentionReasons(sources, checkedAt);
        String healthStatus = availableSources == 0
                ? "UNAVAILABLE"
                : visibleCount == 0 && !degraded ? "STABLE" : "ATTENTION";
        return ProjectionHealthDTO.builder()
                .projectionType(definition.type())
                .displayName(definition.displayName())
                .healthStatus(healthStatus)
                .issueCount(visibleCount)
                .issueCountCapped(boundedCount > ISSUE_COUNT_CAP)
                .available(availableSources > 0)
                .reconciliationSupported(false)
                .backlogCount(Math.min(backlogCount, ISSUE_COUNT_CAP))
                .overdueCount(Math.min(dueOutcomeRevisitCount, ISSUE_COUNT_CAP))
                .backlogAgeSeconds(backlogAgeSeconds)
                .oldestBacklogAt(oldestBacklogAt)
                .repairMode("DIAGNOSIS_ONLY")
                .checkedAt(checkedAt)
                .attentionReasons(attentionReasons)
                .build();
    }

    private KnowledgeLifecycleSource readKnowledgeLifecycleSource(
            String source,
            Set<String> requiredTables,
            Set<String> existingTables,
            Supplier<KnowledgeLifecycleSourceHealthRow> query) {
        List<String> missingTables = requiredTables.stream()
                .filter(table -> !existingTables.contains(table))
                .sorted()
                .toList();
        if (!missingTables.isEmpty()) {
            return new KnowledgeLifecycleSource(
                    source, 0, null, "TABLE_MISSING:" + String.join(",", missingTables));
        }
        try {
            KnowledgeLifecycleSourceHealthRow row = query.get();
            return new KnowledgeLifecycleSource(
                    source,
                    row == null || row.getIssueCount() == null
                            ? 0 : Math.max(0, row.getIssueCount()),
                    row == null ? null : row.getOldestIssueAt(),
                    null);
        } catch (RuntimeException ex) {
            return new KnowledgeLifecycleSource(source, 0, null, "READ_UNAVAILABLE");
        }
    }

    private static long boundedKnowledgeCount(List<KnowledgeLifecycleSource> sources) {
        long count = 0;
        for (KnowledgeLifecycleSource source : sources) {
            count = Math.min(ISSUE_COUNT_CAP + 1L, count + Math.max(0, source.count()));
        }
        return count;
    }

    private static List<String> knowledgeLifecycleAttentionReasons(
            List<KnowledgeLifecycleSource> sources,
            LocalDateTime checkedAt) {
        List<String> reasons = new ArrayList<>();
        for (KnowledgeLifecycleSource source : sources) {
            if (!source.available()) {
                reasons.add("SOURCE_ERROR " + source.source() + "=" + source.sourceError());
                continue;
            }
            if (source.count() <= 0) {
                continue;
            }
            long oldestAgeSeconds = source.oldestIssueAt() == null
                    ? 0
                    : Math.max(0, Duration.between(source.oldestIssueAt(), checkedAt).getSeconds());
            reasons.add(source.source() + " count="
                    + Math.min(source.count(), ISSUE_COUNT_CAP)
                    + " oldestAgeSeconds=" + oldestAgeSeconds);
        }
        return List.copyOf(reasons);
    }

    private PageResult<ProjectionIssueDTO> knowledgeLifecycleIssues(
            Set<String> existingTables,
            KnowledgeLifecycleCursor cursor,
            int size) {
        int fetchLimit = size + 1;
        List<KnowledgeLifecycleIssue> rows = new ArrayList<>();
        Map<String, String> sourceErrors = new LinkedHashMap<>();
        readKnowledgeLifecycleIssues(
                PENDING_SUGGESTIONS,
                sourceOrder(PENDING_SUGGESTIONS),
                Set.of("t_int_content_suggestion"),
                existingTables,
                () -> mapper.listPendingSuggestionIssues(
                        cursor.issueId(),
                        cursor.includeCursorId(sourceOrder(PENDING_SUGGESTIONS)),
                        fetchLimit),
                rows,
                sourceErrors);
        readKnowledgeLifecycleIssues(
                BROKEN_REFERENCES,
                sourceOrder(BROKEN_REFERENCES),
                Set.of("t_post_reference"),
                existingTables,
                () -> mapper.listBrokenReferenceIssues(
                        cursor.issueId(),
                        cursor.includeCursorId(sourceOrder(BROKEN_REFERENCES)),
                        fetchLimit),
                rows,
                sourceErrors);
        readKnowledgeLifecycleIssues(
                PENDING_RELATIONS,
                sourceOrder(PENDING_RELATIONS),
                Set.of("t_post_knowledge_relation"),
                existingTables,
                () -> mapper.listPendingKnowledgeRelationIssues(
                        cursor.issueId(),
                        cursor.includeCursorId(sourceOrder(PENDING_RELATIONS)),
                        fetchLimit),
                rows,
                sourceErrors);
        readKnowledgeLifecycleIssues(
                INVALID_PUBLIC_RELATION_TARGETS,
                sourceOrder(INVALID_PUBLIC_RELATION_TARGETS),
                Set.of("t_post_knowledge_relation", "t_post_main"),
                existingTables,
                () -> mapper.listInvalidPublicRelationTargetIssues(
                        cursor.issueId(),
                        cursor.includeCursorId(sourceOrder(INVALID_PUBLIC_RELATION_TARGETS)),
                        fetchLimit),
                rows,
                sourceErrors);
        readKnowledgeLifecycleIssues(
                DUE_OUTCOME_REVISITS,
                sourceOrder(DUE_OUTCOME_REVISITS),
                Set.of("t_int_post_outcome"),
                existingTables,
                () -> mapper.listDueOutcomeRevisitIssues(
                        cursor.issueId(),
                        cursor.includeCursorId(sourceOrder(DUE_OUTCOME_REVISITS)),
                        fetchLimit),
                rows,
                sourceErrors);
        readKnowledgeLifecycleIssues(
                FRESHNESS_ATTENTION,
                sourceOrder(FRESHNESS_ATTENTION),
                Set.of("t_int_post_trust_state", "t_post_main"),
                existingTables,
                () -> mapper.listFreshnessAttentionIssues(
                        cursor.issueId(),
                        cursor.includeCursorId(sourceOrder(FRESHNESS_ATTENTION)),
                        fetchLimit),
                rows,
                sourceErrors);
        rows.sort(Comparator
                .comparing((KnowledgeLifecycleIssue item) -> item.row().getIssueId(),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparingInt(KnowledgeLifecycleIssue::sourceOrder)
                .thenComparing(item -> item.row().getIssueType() == null
                        ? "" : item.row().getIssueType()));
        boolean degraded = !sourceErrors.isEmpty();
        boolean hasMore = !degraded && rows.size() > size;
        List<KnowledgeLifecycleIssue> visible = rows.stream().limit(size).toList();
        String nextCursor = hasMore && !visible.isEmpty()
                ? KnowledgeLifecycleCursor.from(visible.get(visible.size() - 1)).encode()
                : null;
        PageResult<ProjectionIssueDTO> result = PageResult.of(
                        visible.stream()
                                .map(item -> toIssue(KNOWLEDGE_LIFECYCLE, item.row()))
                                .toList(),
                        nextCursor,
                        hasMore)
                .withMetadata(
                        "knowledge-lifecycle",
                        degraded,
                        degraded ? "KNOWLEDGE_LIFECYCLE_SOURCE_DEGRADED" : null,
                        fetchLimit * 6);
        if (degraded) {
            result.withDiagnostic("sourceErrors", Map.copyOf(sourceErrors));
        }
        return result;
    }

    private void readKnowledgeLifecycleIssues(
            String source,
            int sourceOrder,
            Set<String> requiredTables,
            Set<String> existingTables,
            Supplier<List<IssueRow>> query,
            List<KnowledgeLifecycleIssue> rows,
            Map<String, String> sourceErrors) {
        List<String> missingTables = requiredTables.stream()
                .filter(table -> !existingTables.contains(table))
                .sorted()
                .toList();
        if (!missingTables.isEmpty()) {
            sourceErrors.put(source, "TABLE_MISSING:" + String.join(",", missingTables));
            return;
        }
        try {
            List<IssueRow> sourceRows = query.get();
            if (sourceRows == null) {
                sourceErrors.put(source, "READ_UNAVAILABLE");
                return;
            }
            for (IssueRow row : sourceRows) {
                if (row == null || row.getIssueId() == null || row.getIssueId() <= 0) {
                    sourceErrors.put(source, "INVALID_ROW");
                    continue;
                }
                rows.add(new KnowledgeLifecycleIssue(source, sourceOrder, row));
            }
        } catch (RuntimeException ex) {
            sourceErrors.put(source, "READ_UNAVAILABLE");
        }
    }

    private static long parseNumericCursor(String rawCursor) {
        String value = normalizeCursor(rawCursor);
        if (value.isEmpty() || "0".equals(value)) {
            return 0L;
        }
        if (!value.matches("[0-9]+")) {
            throw invalidCursor();
        }
        try {
            long cursor = Long.parseLong(value);
            if (cursor < 0) {
                throw invalidCursor();
            }
            return cursor;
        } catch (NumberFormatException ex) {
            throw invalidCursor();
        }
    }

    private static int sourceOrder(String source) {
        int order = KNOWLEDGE_SOURCE_ORDER.indexOf(source);
        if (order < 0) {
            throw new IllegalStateException("unknown knowledge lifecycle source: " + source);
        }
        return order;
    }

    private static BizException invalidCursor() {
        return new BizException(ErrorCode.PARAM_ERROR.getCode(), "cursor is invalid");
    }

    private static String normalizeCursor(String rawCursor) {
        String value = rawCursor == null ? "" : rawCursor;
        if (value.length() > MAX_CURSOR_LENGTH) {
            throw invalidCursor();
        }
        return value.trim();
    }

    private ProjectionReconcileResultDTO reconcileIncentiveAccount(
            ReconcileRequest request,
            String fingerprint,
            Long operatorUid) {
        ReconciliationDTO delegated = request.dryRun()
                ? incentiveReconciliationFacade.previewAccountProjection(
                request.limit(), request.reason(), operatorUid)
                : incentiveReconciliationFacade.reconcileAccountProjection(
                request.limit(), request.reason(), operatorUid);
        return result(
                "INCENTIVE_ACCOUNT",
                request,
                fingerprint,
                request.dryRun()
                        ? "DIAGNOSTIC_PREVIEW_COMPLETED"
                        : "DIAGNOSTIC_SCAN_COMPLETED",
                delegated.getRunId(),
                null,
                delegated.getScannedCount(),
                delegated.getMismatchCount(),
                0,
                null,
                null,
                delegated.getCoverageComplete(),
                operatorUid);
    }

    private ProjectionReconcileResultDTO reconcileRewardInboxDelivery(
            ReconcileRequest request,
            String fingerprint,
            Long operatorUid) {
        if (request.dryRun()) {
            int due = incentiveReconciliationFacade.previewOverdueRewardInbox(
                    request.limit(), operatorUid);
            int boundedDue = Math.min(Math.max(0, due), request.limit());
            return result(
                    "REWARD_INBOX_DELIVERY",
                    request,
                    fingerprint,
                    "DRY_RUN_COMPLETED",
                    null,
                    IncentiveProjectionReconciliationFacade.REWARD_INBOX_SLA_MINUTES,
                    boundedDue,
                    boundedDue,
                    0,
                    0,
                    0,
                    due <= request.limit(),
                    operatorUid);
        }
        RewardInboxReconcileDTO delegated =
                incentiveReconciliationFacade.reconcileOverdueRewardInbox(
                        request.limit(), request.reason(), operatorUid);
        int applied = nonNegative(delegated.getAppliedCount());
        int rejected = nonNegative(delegated.getRejectedCount());
        return result(
                "REWARD_INBOX_DELIVERY",
                request,
                fingerprint,
                "COMPLETED",
                null,
                IncentiveProjectionReconciliationFacade.REWARD_INBOX_SLA_MINUTES,
                delegated.getProcessedCount(),
                delegated.getProcessedCount(),
                applied + rejected,
                applied,
                rejected,
                delegated.getCoverageComplete(),
                operatorUid);
    }

    private ProjectionReconcileResultDTO reconcileExpiredRoleGrants(
            ReconcileRequest request,
            String fingerprint,
            Long operatorUid) {
        int due = incentiveReconciliationFacade.previewExpiredRoleGrants(
                request.limit(), operatorUid);
        int changed = request.dryRun()
                ? 0
                : incentiveReconciliationFacade.reconcileExpiredRoleGrants(
                request.limit(), request.reason(), operatorUid);
        return result(
                "ROLE_GRANT_EXPIRY",
                request,
                fingerprint,
                request.dryRun() ? "DRY_RUN_COMPLETED" : "COMPLETED",
                null,
                null,
                due,
                due,
                changed,
                null,
                null,
                due < request.limit(),
                operatorUid);
    }

    private static ProjectionReconcileResultDTO result(
            String projectionType,
            ReconcileRequest request,
            String fingerprint,
            String status,
            Long delegatedRunId,
            Integer slaMinutes,
            Integer processedCount,
            Integer issueCount,
            Integer changedCount,
            Integer appliedCount,
            Integer rejectedCount,
            Boolean coverageComplete,
            Long operatorUid) {
        return ProjectionReconcileResultDTO.builder()
                .projectionType(projectionType)
                .status(status)
                .dryRun(request.dryRun())
                .replayed(false)
                .idempotencyKey(request.idempotencyKey())
                .requestFingerprint(fingerprint)
                .delegatedRunId(delegatedRunId)
                .slaMinutes(slaMinutes)
                .processedCount(processedCount == null ? 0 : processedCount)
                .issueCount(issueCount == null ? 0 : issueCount)
                .changedCount(changedCount == null ? 0 : changedCount)
                .appliedCount(appliedCount)
                .rejectedCount(rejectedCount)
                .coverageComplete(Boolean.TRUE.equals(coverageComplete))
                .operatorUid(operatorUid)
                .reason(request.reason())
                .completedAt(LocalDateTime.now())
                .build();
    }

    private ProjectionReconcileResultDTO parseReplay(
            AuditReplayRow replay,
            String fingerprint,
            Long operatorUid) {
        if (!operatorUid.equals(replay.getOperatorUid())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        try {
            ProjectionReconcileResultDTO previous = objectMapper.readValue(
                    replay.getAfterJson(), ProjectionReconcileResultDTO.class);
            if (!fingerprint.equals(previous.getRequestFingerprint())) {
                throw new BizException(ErrorCode.DUPLICATE_OPERATION.getCode(),
                        "idempotencyKey was already used with different reconciliation input");
            }
            return previous;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                    "stored reconciliation audit cannot be replayed safely");
        }
    }

    private void requireOperations(Long uid) {
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_OPS);
    }

    private static ProjectionDefinition requireDefinition(String rawProjectionType) {
        String type = StringUtils.hasText(rawProjectionType)
                ? rawProjectionType.trim().toUpperCase(Locale.ROOT)
                : "";
        ProjectionDefinition definition = DEFINITIONS.get(type);
        if (definition == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return definition;
    }

    private static void requireAvailable(
            ProjectionDefinition definition,
            Set<String> existingTables) {
        if (!existingTables.containsAll(definition.requiredTables())) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                    "projection dependencies are not ready");
        }
    }

    private static ReconcileRequest requireRequest(ProjectionReconcileCmd cmd) {
        if (cmd == null || cmd.getDryRun() == null || cmd.getLimit() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (cmd.getLimit() < 1 || cmd.getLimit() > 100) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String reason = requireText(cmd.getReason(), 500);
        String idempotencyKey = requireText(cmd.getIdempotencyKey(), 96);
        return new ReconcileRequest(cmd.getDryRun(), cmd.getLimit(), reason, idempotencyKey);
    }

    private static String requireText(String value, int max) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private static ProjectionIssueDTO toIssue(String projectionType, IssueRow row) {
        return ProjectionIssueDTO.builder()
                .issueId(row.getIssueId())
                .projectionType(projectionType)
                .issueType(row.getIssueType())
                .severity(row.getSeverity())
                .subjectType(row.getSubjectType())
                .subjectId(row.getSubjectId())
                .summary(row.getSummary())
                .detectedAt(row.getDetectedAt())
                .build();
    }

    private static String fingerprint(String projectionType, ReconcileRequest request) {
        return sha256(projectionType + "|" + request.dryRun() + "|" + request.limit()
                + "|" + request.reason());
    }

    private static String resourceId(Long operatorUid, String projectionType, String idempotencyKey) {
        return sha256(operatorUid + "|" + projectionType + "|" + idempotencyKey);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private ProjectionHealthDTO outboxDeliveryHealth(
            ProjectionDefinition definition,
            LocalDateTime checkedAt) {
        DeliveryHealthRow row = mapper.selectOutboxDeliveryHealth();
        long backlog = row == null || row.getBacklogCount() == null ? 0 : row.getBacklogCount();
        long failed = row == null || row.getFailedCount() == null ? 0 : row.getFailedCount();
        LocalDateTime oldest = row == null ? null : row.getOldestBacklogAt();
        long backlogAgeSeconds = oldest == null ? 0
                : Math.max(0, Duration.between(oldest, checkedAt).getSeconds());
        long issues = Math.min(ISSUE_COUNT_CAP, mapper.countOutboxDeliveryIssues(ISSUE_COUNT_CAP + 1));
        return ProjectionHealthDTO.builder()
                .projectionType(definition.type())
                .displayName(definition.displayName())
                .healthStatus(issues == 0 ? "STABLE" : "ATTENTION")
                .issueCount(issues)
                .issueCountCapped(issues >= ISSUE_COUNT_CAP)
                .available(true)
                .reconciliationSupported(false)
                .watermark(row == null ? null : row.getWatermark())
                .backlogCount(backlog)
                .backlogAgeSeconds(backlogAgeSeconds)
                .oldestBacklogAt(oldest)
                .lastSuccessAt(row == null ? null : row.getLastSuccessAt())
                .lastFailureAt(row == null ? null : row.getLastFailureAt())
                .repairMode("EXISTING_OUTBOX_OPS")
                .checkedAt(checkedAt)
                .attentionReasons(issues == 0 ? List.of() : List.of(
                        failed > 0 ? "存在投递失败的 Outbox 事件" : definition.attentionReason()))
                .build();
    }

    private ProjectionHealthDTO rewardInboxDeliveryHealth(
            ProjectionDefinition definition,
            LocalDateTime checkedAt) {
        LocalDateTime cutoff = rewardInboxCutoff(checkedAt);
        RewardInboxDeliveryHealthRow row = mapper.selectRewardInboxDeliveryHealth();
        long backlog = row == null || row.getBacklogCount() == null ? 0 : row.getBacklogCount();
        LocalDateTime oldest = row == null ? null : row.getOldestBacklogAt();
        long backlogAgeSeconds = oldest == null ? 0
                : Math.max(0, Duration.between(oldest, checkedAt).getSeconds());
        long boundedCount = mapper.countOverdueRewardInboxDeliveryIssues(
                cutoff, ISSUE_COUNT_CAP + 1);
        boolean capped = boundedCount > ISSUE_COUNT_CAP;
        long visibleCount = Math.min(boundedCount, ISSUE_COUNT_CAP);
        return ProjectionHealthDTO.builder()
                .projectionType(definition.type())
                .displayName(definition.displayName())
                .healthStatus(visibleCount == 0 ? "STABLE" : "ATTENTION")
                .issueCount(visibleCount)
                .issueCountCapped(capped)
                .available(true)
                .reconciliationSupported(true)
                .slaMinutes(IncentiveProjectionReconciliationFacade.REWARD_INBOX_SLA_MINUTES)
                .watermark(row == null ? null : row.getWatermark())
                .backlogCount(backlog)
                .overdueCount(visibleCount)
                .backlogAgeSeconds(backlogAgeSeconds)
                .oldestBacklogAt(oldest)
                .lastSuccessAt(row == null ? null : row.getLastSuccessAt())
                .lastFailureAt(row == null ? null : row.getLastFailureAt())
                .repairMode("BOUNDED_EXISTING_INBOX_PROCESSING")
                .checkedAt(checkedAt)
                .attentionReasons(visibleCount == 0
                        ? List.of()
                        : List.of(definition.attentionReason()))
                .build();
    }

    private static LocalDateTime rewardInboxCutoff(LocalDateTime now) {
        return now.minusMinutes(
                IncentiveProjectionReconciliationFacade.REWARD_INBOX_SLA_MINUTES);
    }

    private static int nonNegative(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private static Map<String, ProjectionDefinition> definitions() {
        Map<String, ProjectionDefinition> definitions = new LinkedHashMap<>();
        register(definitions, "INCENTIVE_ACCOUNT", "激励账户与账本",
                true,
                "最新对账批次发现账户与账本差异",
                "t_admin_audit_log", "t_projection_reconcile_request",
                "t_incentive_account", "t_incentive_ledger",
                "t_incentive_reconciliation_run", "t_incentive_reconciliation_item");
        register(definitions, "OUTBOX_DELIVERY", "Outbox 事件投递",
                false,
                "存在失败、锁过期或长时间停滞的 Outbox 事件",
                "t_outbox_message");
        register(definitions, "REWARD_INBOX_DELIVERY", "奖励 Inbox 投递",
                true,
                "存在超过 60 分钟 SLA 仍为 PENDING 的奖励 Inbox",
                "t_admin_audit_log", "t_projection_reconcile_request",
                "t_incentive_reward_inbox");
        register(definitions, "ROLE_GRANT_EXPIRY", "角色到期状态",
                true,
                "存在已到期但尚未收口的角色授权",
                "t_admin_audit_log", "t_projection_reconcile_request",
                "t_community_role_grant");
        register(definitions, "ROLE_MAINTENANCE_ACCESS", "角色与维护任务权限",
                false,
                "存在受派人已无有效领域维护角色的进行中任务",
                "t_community_role_definition", "t_community_role_grant",
                "t_incentive_freeze_record", "t_collab_content_maintenance_task");
        register(definitions, "PUBLIC_CONTRIBUTION_VISIBILITY", "公共贡献可见性",
                false,
                "存在已完成维护任务指向当前不可公开的交付资源",
                "t_collab_content_maintenance_task", "t_post_main",
                "t_collab_series", "t_collab_series_submission");
        register(definitions, "COLLABORATION_TIMELINE", "协作公开时间线",
                false,
                "存在已完成需求缺少公开完成事件",
                "t_collab_content_need", "t_collab_content_need_event");
        register(definitions, "NOTIFICATION_DELIVERY", "通知投递重试",
                false,
                "存在失败、锁过期或长时间停滞的通知重试任务",
                "t_notif_retry_task");
        register(definitions, "SEARCH_INDEX_QUEUE", "搜索索引重试",
                false,
                "存在失败、锁过期或长时间停滞的搜索索引任务",
                "t_search_index_retry_task");
        register(definitions, "FEED_FEEDBACK_VISIBILITY", "Feed 控制目标可见性",
                false,
                "存在仍有效但目标内容已不可公开的 Feed 控制",
                "t_feed_feedback_preference", "t_post_main");
        register(definitions, "TOPIC_SPACE_VISIBILITY", "主题空间关联可见性",
                false,
                "存在主题空间关联指向已删除、隐藏或未发布内容",
                "t_collab_topic_post", "t_post_main");
        register(definitions, KNOWLEDGE_LIFECYCLE, "知识生命周期",
                false,
                "知识生命周期存在待处理、失效引用、公开目标异常、到期复访或新鲜度关注项",
                "t_int_content_suggestion", "t_post_reference",
                "t_post_knowledge_relation", "t_post_main", "t_int_post_outcome",
                "t_int_post_trust_state");
        return Map.copyOf(definitions);
    }

    private static void register(
            Map<String, ProjectionDefinition> definitions,
            String type,
            String displayName,
            boolean reconciliationSupported,
            String attentionReason,
            String... requiredTables) {
        definitions.put(type, new ProjectionDefinition(
                type,
                displayName,
                Set.of(requiredTables),
                reconciliationSupported,
                attentionReason));
    }

    private record ProjectionDefinition(
            String type,
            String displayName,
            Set<String> requiredTables,
            boolean reconciliationSupported,
            String attentionReason) {
    }

    private record KnowledgeLifecycleSource(
            String source,
            long count,
            LocalDateTime oldestIssueAt,
            String sourceError) {

        private boolean available() {
            return sourceError == null;
        }
    }

    private record KnowledgeLifecycleIssue(
            String source,
            int sourceOrder,
            IssueRow row) {
    }

    private record KnowledgeLifecycleCursor(
            long issueId,
            int sourceOrder,
            String source) {

        private static KnowledgeLifecycleCursor parse(String rawCursor) {
            String value = normalizeCursor(rawCursor);
            if (value.isEmpty() || "0".equals(value)) {
                return new KnowledgeLifecycleCursor(0L, -1, null);
            }
            if (value.matches("[0-9]+")) {
                try {
                    long legacyId = Long.parseLong(value);
                    if (legacyId < 0) {
                        throw invalidCursor();
                    }
                    return new KnowledgeLifecycleCursor(
                            legacyId, LEGACY_CURSOR_SOURCE_ORDER, null);
                } catch (NumberFormatException ex) {
                    throw invalidCursor();
                }
            }
            if (value.contains("=")) {
                throw invalidCursor();
            }
            try {
                byte[] decodedBytes = Base64.getUrlDecoder().decode(value);
                String canonical = Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(decodedBytes);
                if (!canonical.equals(value)) {
                    throw invalidCursor();
                }
                String decoded = new String(decodedBytes, StandardCharsets.UTF_8);
                String[] parts = decoded.split("\\|", -1);
                if (parts.length != 3
                        || !"kl1".equals(parts[0])
                        || !parts[1].matches("[1-9][0-9]*")) {
                    throw invalidCursor();
                }
                long id = Long.parseLong(parts[1]);
                int sourceOrder = ProjectionHealthService.sourceOrder(parts[2]);
                if (id <= 0) {
                    throw invalidCursor();
                }
                return new KnowledgeLifecycleCursor(id, sourceOrder, parts[2]);
            } catch (BizException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                throw invalidCursor();
            }
        }

        private boolean includeCursorId(int currentSourceOrder) {
            return issueId > 0
                    && source != null
                    && currentSourceOrder > sourceOrder;
        }

        private static KnowledgeLifecycleCursor from(KnowledgeLifecycleIssue item) {
            if (item == null
                    || item.row() == null
                    || item.row().getIssueId() == null
                    || item.row().getIssueId() <= 0
                    || item.source() == null) {
                throw invalidCursor();
            }
            return new KnowledgeLifecycleCursor(
                    item.row().getIssueId(),
                    item.sourceOrder(),
                    item.source());
        }

        private String encode() {
            String raw = "kl1|" + issueId + "|" + source;
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }
    }

    private record ReconcileRequest(
            boolean dryRun,
            int limit,
            String reason,
            String idempotencyKey) {
    }
}
