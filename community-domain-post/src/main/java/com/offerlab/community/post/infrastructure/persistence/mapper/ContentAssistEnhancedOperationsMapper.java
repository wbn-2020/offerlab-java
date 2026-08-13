package com.offerlab.community.post.infrastructure.persistence.mapper;

import com.offerlab.community.post.infrastructure.persistence.po.ContentAssistEnhancedExceptionRow;
import com.offerlab.community.post.infrastructure.persistence.po.ContentAssistEnhancedReconcileRequestPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ContentAssistEnhancedOperationsMapper {
    @Select("""
            SELECT r.id AS requestId,
                   r.uid AS requestUid,
                   r.usage_id AS usageId,
                   u.user_id AS usageUid,
                   u.usage_status AS usageStatus,
                   u.source_ref AS usageSourceRef,
                   r.request_status AS requestStatus,
                   r.request_fingerprint AS requestFingerprint,
                   r.error_code AS errorCode,
                   r.provider AS provider,
                   r.prompt_tokens AS promptTokens,
                   r.completion_tokens AS completionTokens,
                   r.estimated_cost_micros AS estimatedCostMicros,
                   TIMESTAMPDIFF(SECOND, r.update_time, CURRENT_TIMESTAMP(3)) AS ageSeconds,
                   r.create_time AS createTime,
                   r.update_time AS updateTime
            FROM t_content_assist_enhanced_request r
            LEFT JOIN t_virtual_benefit_entitlement_usage u ON u.id = r.usage_id
            WHERE r.consumer_code = 'CONTENT_ASSIST_ENHANCED'
              AND (
                    (r.request_status = 'RUNNING'
                        AND r.update_time <= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL #{timeoutSeconds} SECOND))
                 OR (r.request_status = 'RUNNING'
                        AND (u.id IS NULL OR u.usage_status <> 'RESERVED'))
                 OR (r.request_status = 'SUCCEEDED'
                        AND (u.id IS NULL OR u.usage_status <> 'CONFIRMED'))
                 OR (r.request_status IN ('FALLBACK', 'FAILED')
                        AND r.usage_id IS NOT NULL
                        AND (u.id IS NULL OR u.usage_status <> 'RELEASED'))
                 OR (u.id IS NOT NULL
                        AND (u.user_id <> r.uid OR u.source_ref <> CAST(r.id AS CHAR)))
              )
            ORDER BY r.update_time ASC, r.id ASC
            LIMIT #{limit}
            """)
    List<ContentAssistEnhancedExceptionRow> selectExceptions(
            @Param("timeoutSeconds") long timeoutSeconds,
            @Param("limit") int limit);

    @Select("""
            SELECT r.id AS requestId,
                   r.uid AS requestUid,
                   r.usage_id AS usageId,
                   u.user_id AS usageUid,
                   u.usage_status AS usageStatus,
                   u.source_ref AS usageSourceRef,
                   r.request_status AS requestStatus,
                   r.request_fingerprint AS requestFingerprint,
                   r.error_code AS errorCode,
                   r.provider AS provider,
                   r.prompt_tokens AS promptTokens,
                   r.completion_tokens AS completionTokens,
                   r.estimated_cost_micros AS estimatedCostMicros,
                   TIMESTAMPDIFF(SECOND, r.update_time, CURRENT_TIMESTAMP(3)) AS ageSeconds,
                   r.create_time AS createTime,
                   r.update_time AS updateTime
            FROM t_content_assist_enhanced_request r
            LEFT JOIN t_virtual_benefit_entitlement_usage u ON u.id = r.usage_id
            WHERE r.consumer_code = 'CONTENT_ASSIST_ENHANCED'
              AND (#{cursor} IS NULL OR r.id > #{cursor})
              AND (
                    (r.request_status = 'RUNNING'
                        AND r.update_time <= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL #{timeoutSeconds} SECOND))
                 OR (r.request_status = 'RUNNING'
                        AND (u.id IS NULL OR u.usage_status <> 'RESERVED'))
                 OR (r.request_status = 'SUCCEEDED'
                        AND (u.id IS NULL OR u.usage_status <> 'CONFIRMED'))
                 OR (r.request_status IN ('FALLBACK', 'FAILED')
                        AND r.usage_id IS NOT NULL
                        AND (u.id IS NULL OR u.usage_status <> 'RELEASED'))
                 OR (u.id IS NOT NULL
                        AND (u.user_id <> r.uid OR u.source_ref <> CAST(r.id AS CHAR)))
              )
            ORDER BY r.id ASC
            LIMIT #{limit}
            """)
    List<ContentAssistEnhancedExceptionRow> selectExceptionPage(
            @Param("timeoutSeconds") long timeoutSeconds,
            @Param("cursor") Long cursor,
            @Param("limit") int limit);

    @Select("""
            SELECT * FROM t_content_assist_enhanced_reconcile_request
            WHERE operator_uid = #{operatorUid} AND idempotency_key = #{idempotencyKey}
            LIMIT 1
            """)
    ContentAssistEnhancedReconcileRequestPO selectByOperatorAndKey(
            @Param("operatorUid") Long operatorUid,
            @Param("idempotencyKey") String idempotencyKey);

    @Insert("""
            INSERT INTO t_content_assist_enhanced_reconcile_request(
                id, operator_uid, idempotency_key, request_fingerprint, request_status
            ) VALUES (
                #{id}, #{operatorUid}, #{idempotencyKey}, #{requestFingerprint}, 'RUNNING'
            )
            """)
    int insertRunning(ContentAssistEnhancedReconcileRequestPO request);

    @Select("""
            SELECT * FROM t_content_assist_enhanced_reconcile_request
            WHERE operator_uid = #{operatorUid} AND idempotency_key = #{idempotencyKey}
            LIMIT 1 FOR UPDATE
            """)
    ContentAssistEnhancedReconcileRequestPO lockByOperatorAndKey(
            @Param("operatorUid") Long operatorUid,
            @Param("idempotencyKey") String idempotencyKey);

    @Update("""
            UPDATE t_content_assist_enhanced_reconcile_request
            SET request_status = 'COMPLETED', result_json = #{resultJson}
            WHERE id = #{id} AND request_status = 'RUNNING'
            """)
    int complete(@Param("id") Long id, @Param("resultJson") String resultJson);
}
