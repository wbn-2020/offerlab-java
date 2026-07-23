package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.controller.ProjectionHealthController;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectionHealthGuardTest {

    private static final Set<String> KNOWLEDGE_ISSUE_TYPES = Set.of(
            "CONTENT_SUGGESTION_PENDING",
            "POST_REFERENCE_BROKEN",
            "KNOWLEDGE_RELATION_PENDING",
            "KNOWLEDGE_RELATION_TARGET_NOT_PUBLIC",
            "POST_OUTCOME_REVISIT_DUE",
            "POST_FRESHNESS_POSSIBLY_STALE",
            "POST_FRESHNESS_AWAITING_CONFIRMATION");

    @Test
    void projectionHealthEndpointsAreProtectedAndBounded() throws Exception {
        String controller = read(
                "src/main/java/com/offerlab/community/analytics/controller/ProjectionHealthController.java");
        String service = read(
                "src/main/java/com/offerlab/community/analytics/application/ProjectionHealthService.java");
        String mapper = read(
                "src/main/java/com/offerlab/community/analytics/infrastructure/persistence/mapper/ProjectionHealthMapper.java");
        String freshnessHealthSql = selectSql(mapper, "selectFreshnessAttentionHealth");
        String freshnessIssueSql = selectSql(mapper, "listFreshnessAttentionIssues");
        String[] knowledgeIssueMethods = {
                "listPendingSuggestionIssues",
                "listBrokenReferenceIssues",
                "listPendingKnowledgeRelationIssues",
                "listInvalidPublicRelationTargetIssues",
                "listDueOutcomeRevisitIssues",
                "listFreshnessAttentionIssues"
        };

        assertTrue(controller.contains("@RequestMapping(\"/api/v1/admin/community-health/projections\")"));
        assertTrue(controller.contains("@RateLimit"));
        assertTrue(controller.contains("UserContext.require()"));
        assertTrue(controller.contains("@RequestParam(defaultValue = \"0\") @Size(max = 256) String cursor"));
        assertTrue(controller.contains("@Valid @RequestBody ProjectionReconcileCmd"));
        assertTrue(ProjectionHealthController.class.isAnnotationPresent(Validated.class));
        Method issuesEndpoint = ProjectionHealthController.class.getDeclaredMethod(
                "issues", String.class, String.class, int.class);
        RateLimit issueRateLimit = issuesEndpoint.getAnnotation(RateLimit.class);
        assertNotNull(issueRateLimit);
        assertEquals(60, issueRateLimit.rate());
        assertEquals(60, issueRateLimit.per());
        assertFalse(issueRateLimit.failOpen());
        RequestParam cursorRequestParam = parameterAnnotation(
                issuesEndpoint, 1, RequestParam.class);
        Size cursorSize = parameterAnnotation(issuesEndpoint, 1, Size.class);
        assertEquals("0", cursorRequestParam.defaultValue());
        assertEquals(256, cursorSize.max());
        assertTrue(service.contains("AdminPermissionService.ROLE_OPS"));
        assertTrue(service.contains("requireWritable"));
        assertTrue(service.contains("idempotencyKey"));
        assertTrue(service.contains("reserveReconciliationRequest"));
        assertTrue(service.contains("DRY_RUN_COMPLETED"));
        assertTrue(service.contains("diagnosis-only"));
        assertTrue(service.contains("\"REWARD_INBOX_DELIVERY\""));
        assertTrue(service.contains("REWARD_INBOX_SLA_MINUTES"));
        assertTrue(service.contains("previewOverdueRewardInbox"));
        assertTrue(service.contains("reconcileOverdueRewardInbox"));
        assertTrue(service.contains("DIAGNOSTIC_SCAN_AND_DIFFERENCE_RECORDING"));
        assertTrue(service.contains("DIAGNOSTIC_SCAN_COMPLETED"));
        assertTrue(service.contains("\"KNOWLEDGE_LIFECYCLE\""));
        assertTrue(service.contains("KnowledgeLifecycleCursor"));
        assertTrue(service.contains("Base64.getUrlEncoder()"));
        assertTrue(service.contains(".withoutPadding()"));
        assertTrue(service.contains("hasMore = !degraded"));
        assertTrue(service.contains("LEGACY_CURSOR_SOURCE_ORDER"));
        assertTrue(service.contains("\"FRESHNESS_ATTENTION\""));
        assertTrue(service.contains("selectFreshnessAttentionHealth"));
        assertTrue(service.contains("listFreshnessAttentionIssues"));
        assertTrue(service.contains(
                ".filter(source -> !FRESHNESS_ATTENTION.equals(source.source()))"));
        assertTrue(service.contains("SOURCE_ERROR"));
        assertTrue(service.contains(".repairMode(\"DIAGNOSIS_ONLY\")"));
        assertTrue(mapper.contains("LIMIT #{cap}"));
        assertTrue(mapper.contains("ORDER BY"));
        assertTrue(mapper.contains("t_admin_audit_log"));
        assertTrue(mapper.contains("t_incentive_reward_inbox"));
        assertTrue(mapper.contains("inbox_status = 'PENDING'"));
        assertTrue(mapper.contains("listOverdueRewardInboxDeliveryIssues"));
        assertTrue(mapper.contains("selectPendingSuggestionHealth"));
        assertTrue(mapper.contains("resolution = 'PENDING'"));
        assertTrue(mapper.contains("selectBrokenReferenceHealth"));
        assertTrue(mapper.contains("reference_status = 'BROKEN'"));
        assertTrue(mapper.contains("selectPendingKnowledgeRelationHealth"));
        assertTrue(mapper.contains("review_status = 'PENDING'"));
        assertTrue(mapper.contains("selectInvalidPublicRelationTargetHealth"));
        assertTrue(mapper.contains("visibility_status = 'VISIBLE'"));
        assertTrue(mapper.contains("selectDueOutcomeRevisitHealth"));
        assertTrue(mapper.contains("follow_up_at <= CURRENT_TIMESTAMP(3)"));
        assertTrue(mapper.contains("'t_int_post_trust_state'"));
        assertTrue(mapper.contains("selectFreshnessAttentionHealth"));
        assertTrue(mapper.contains("listFreshnessAttentionIssues"));
        for (String method : knowledgeIssueMethods) {
            String sql = selectSql(mapper, method);
            assertTrue(sql.contains("#{cursor} = 0"));
            assertTrue(sql.contains("#{includeCursorId} = 1"));
            assertTrue(sql.contains("LIMIT #{limit}"));
            assertFalse(sql.matches("(?is).*(UPDATE|INSERT|DELETE)\\s+.*"));
        }
        assertKnowledgeSourceSql(
                mapper,
                "listPendingSuggestionIssues",
                "id",
                "t_int_content_suggestion",
                List.of("resolution = 'PENDING'"),
                Set.of("CONTENT_SUGGESTION_PENDING"));
        assertKnowledgeSourceSql(
                mapper,
                "listBrokenReferenceIssues",
                "id",
                "t_post_reference",
                List.of("reference_status = 'BROKEN'", "is_deleted = 0"),
                Set.of("POST_REFERENCE_BROKEN"));
        assertKnowledgeSourceSql(
                mapper,
                "listPendingKnowledgeRelationIssues",
                "id",
                "t_post_knowledge_relation",
                List.of("review_status = 'PENDING'", "is_deleted = 0"),
                Set.of("KNOWLEDGE_RELATION_PENDING"));
        assertKnowledgeSourceSql(
                mapper,
                "listInvalidPublicRelationTargetIssues",
                "relation.id",
                "t_post_knowledge_relation",
                List.of(
                        "review_status = 'APPROVED'",
                        "visibility_status = 'VISIBLE'",
                        "target_post.visibility <> 1"),
                Set.of("KNOWLEDGE_RELATION_TARGET_NOT_PUBLIC"));
        assertKnowledgeSourceSql(
                mapper,
                "listDueOutcomeRevisitIssues",
                "id",
                "t_int_post_outcome",
                List.of(
                        "outcome_status = 'ACTIVE'",
                        "follow_up_at <= CURRENT_TIMESTAMP(3)"),
                Set.of("POST_OUTCOME_REVISIT_DUE"));
        assertKnowledgeSourceSql(
                mapper,
                "listFreshnessAttentionIssues",
                "state.post_id",
                "t_int_post_trust_state",
                List.of(
                        "state.freshness_status IN (",
                        "post.is_deleted = 0",
                        "post.post_status = 1",
                        "post.visibility = 1"),
                Set.of(
                        "POST_FRESHNESS_POSSIBLY_STALE",
                        "POST_FRESHNESS_AWAITING_CONFIRMATION"));
        assertTrue(mapper.contains("'CONTENT_SUGGESTION_PENDING'"));
        assertTrue(mapper.contains("'POST_REFERENCE_BROKEN'"));
        assertTrue(mapper.contains("'KNOWLEDGE_RELATION_PENDING'"));
        assertTrue(mapper.contains("'KNOWLEDGE_RELATION_TARGET_NOT_PUBLIC'"));
        assertTrue(mapper.contains("'POST_OUTCOME_REVISIT_DUE'"));
        assertFreshnessFilter(freshnessHealthSql);
        assertFreshnessFilter(freshnessIssueSql);
        assertTrue(freshnessHealthSql.contains("state.update_time AS issueAt"));
        assertTrue(freshnessIssueSql.contains("state.update_time AS detectedAt"));
        assertTrue(freshnessIssueSql.contains("CAST(state.post_id AS CHAR) AS subjectId"));
        assertTrue(freshnessIssueSql.matches(
                "(?s).*CASE state\\.freshness_status\\s+"
                        + "WHEN 'POSSIBLY_STALE'\\s+"
                        + "THEN 'POST_FRESHNESS_POSSIBLY_STALE'\\s+"
                        + "WHEN 'AWAITING_AUTHOR_CONFIRMATION'\\s+"
                        + "THEN 'POST_FRESHNESS_AWAITING_CONFIRMATION'\\s+"
                        + "END AS issueType.*"));
        assertTrue(freshnessIssueSql.matches(
                "(?s).*CASE state\\.freshness_status\\s+"
                        + "WHEN 'POSSIBLY_STALE' THEN 'MEDIUM'\\s+"
                        + "WHEN 'AWAITING_AUTHOR_CONFIRMATION' THEN 'HIGH'\\s+"
                        + "END AS severity.*"));
        assertFalse(service.contains("POST_FRESHNESS_POSSIBLY_STALE"));
        assertFalse(service.contains("POST_FRESHNESS_AWAITING_CONFIRMATION"));
        assertTrue(mapper.contains("INSERT IGNORE INTO t_projection_reconcile_request"));
        assertTrue(mapper.contains("FOR UPDATE"));
        assertFalse(mapper.contains("UPDATE t_post_reference"));
        assertFalse(mapper.contains("UPDATE t_post_knowledge_relation"));
        assertFalse(mapper.contains("UPDATE t_int_post_outcome"));
        assertFalse(mapper.contains("UPDATE t_int_post_trust_state"));

        String combined = (controller + service + mapper).toLowerCase();
        assertFalse(combined.contains("searchcontentgapfulfillmentlistener"));
        assertFalse(combined.contains("leaderboard"));
    }

    private static void assertKnowledgeSourceSql(
            String mapper,
            String methodName,
            String idExpression,
            String tableName,
            List<String> requiredFilters,
            Set<String> expectedIssueTypes) {
        String sql = selectSql(mapper, methodName);
        assertTrue(sql.contains(idExpression + " < #{cursor}"),
                methodName + " must keep the descending keyset boundary");
        assertTrue(sql.contains(idExpression + " = #{cursor}"),
                methodName + " must include the cursor id only for later sources");
        assertTrue(sql.contains("FROM " + tableName),
                methodName + " must keep its owning source table");
        assertTrue(sql.contains("ORDER BY " + idExpression + " DESC"),
                methodName + " must fetch the correct descending source window");
        for (String filter : requiredFilters) {
            assertTrue(sql.contains(filter),
                    methodName + " must keep filter " + filter);
        }
        for (String issueType : KNOWLEDGE_ISSUE_TYPES) {
            assertEquals(expectedIssueTypes.contains(issueType),
                    sql.contains("'" + issueType + "'"),
                    methodName + " has an unexpected issueType mapping for " + issueType);
        }
    }

    private static <A extends Annotation> A parameterAnnotation(
            Method method,
            int parameterIndex,
            Class<A> annotationType) {
        for (Annotation annotation : method.getParameterAnnotations()[parameterIndex]) {
            if (annotationType.isInstance(annotation)) {
                return annotationType.cast(annotation);
            }
        }
        throw new AssertionError("missing " + annotationType.getSimpleName()
                + " on parameter " + parameterIndex + " of " + method.getName());
    }

    private static void assertFreshnessFilter(String sql) {
        assertTrue(sql.contains("state.freshness_status IN ("));
        assertTrue(sql.contains("'POSSIBLY_STALE'"));
        assertTrue(sql.contains("'AWAITING_AUTHOR_CONFIRMATION'"));
        assertTrue(sql.contains("INNER JOIN t_post_main post ON post.id = state.post_id"));
        assertTrue(sql.contains("AND post.is_deleted = 0"));
        assertTrue(sql.contains("AND post.post_status = 1"));
        assertTrue(sql.contains("AND post.visibility = 1"));
    }

    private static String selectSql(String source, String methodName) {
        int methodIndex = source.indexOf(methodName);
        assertTrue(methodIndex >= 0, "missing mapper method " + methodName);
        int selectIndex = source.lastIndexOf("@Select(\"\"\"", methodIndex);
        assertTrue(selectIndex >= 0, "missing @Select for mapper method " + methodName);
        return source.substring(selectIndex, methodIndex);
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
