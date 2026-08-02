package com.offerlab.community.post.infrastructure.persistence.mapper;

import com.offerlab.community.post.infrastructure.persistence.po.ContentAssistEnhancedRequestPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ContentAssistEnhancedRequestMapper {
    @Select("""
            SELECT * FROM t_content_assist_enhanced_request
            WHERE uid = #{uid} AND consumer_code = #{consumerCode} AND idempotency_key = #{idempotencyKey}
            LIMIT 1
            """)
    ContentAssistEnhancedRequestPO selectByRequest(@Param("uid") Long uid,
                                                   @Param("consumerCode") String consumerCode,
                                                   @Param("idempotencyKey") String idempotencyKey);

    @Select("""
            SELECT * FROM t_content_assist_enhanced_request
            WHERE id = #{id} AND uid = #{uid} AND consumer_code = #{consumerCode}
            LIMIT 1
            """)
    ContentAssistEnhancedRequestPO selectByIdAndUid(@Param("id") Long id,
                                                     @Param("uid") Long uid,
                                                     @Param("consumerCode") String consumerCode);

    @Select("""
            SELECT * FROM t_content_assist_enhanced_request
            WHERE uid = #{uid} AND consumer_code = #{consumerCode}
            ORDER BY update_time DESC, id DESC
            LIMIT #{limit}
            """)
    List<ContentAssistEnhancedRequestPO> selectRecentByUser(@Param("uid") Long uid,
                                                            @Param("consumerCode") String consumerCode,
                                                            @Param("limit") int limit);

    @Insert("""
            INSERT INTO t_content_assist_enhanced_request(
                id, uid, consumer_code, idempotency_key, request_fingerprint, content_hash, content_length,
                request_status, prompt_tokens, completion_tokens, estimated_cost_micros
            ) VALUES (
                #{id}, #{uid}, #{consumerCode}, #{idempotencyKey}, #{requestFingerprint}, #{contentHash}, #{contentLength},
                'RUNNING', 0, 0, 0
            )
            """)
    int insertRunning(ContentAssistEnhancedRequestPO request);

    @Update("""
            UPDATE t_content_assist_enhanced_request
            SET usage_id = #{usageId}
            WHERE id = #{id} AND uid = #{uid} AND request_status = 'RUNNING'
            """)
    int attachUsage(@Param("id") Long id, @Param("uid") Long uid, @Param("usageId") Long usageId);

    @Select("""
            SELECT id FROM t_content_assist_enhanced_request
            WHERE id = #{id} AND uid = #{uid} AND request_status = 'RUNNING'
            LIMIT 1 FOR UPDATE
            """)
    Long lockRunningRequest(@Param("id") Long id, @Param("uid") Long uid);

    @Update("""
            UPDATE t_content_assist_enhanced_request
            SET request_status = #{status}, provider = #{provider}, result_json = #{resultJson},
                prompt_tokens = #{promptTokens}, completion_tokens = #{completionTokens},
                estimated_cost_micros = #{estimatedCostMicros}, error_code = #{errorCode},
                completed_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id} AND uid = #{uid} AND request_status = 'RUNNING'
            """)
    int complete(@Param("id") Long id, @Param("uid") Long uid, @Param("status") String status,
                 @Param("provider") String provider, @Param("resultJson") String resultJson,
                 @Param("promptTokens") int promptTokens, @Param("completionTokens") int completionTokens,
                 @Param("estimatedCostMicros") long estimatedCostMicros, @Param("errorCode") String errorCode);

    @Select("""
            SELECT * FROM t_content_assist_enhanced_request
            WHERE request_status = 'RUNNING'
              AND update_time <= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL #{timeoutSeconds} SECOND)
            ORDER BY update_time ASC, id ASC
            LIMIT #{limit}
            """)
    List<ContentAssistEnhancedRequestPO> selectStaleRunning(
            @Param("timeoutSeconds") long timeoutSeconds,
            @Param("limit") int limit);

    @Update("""
            UPDATE t_content_assist_enhanced_request
            SET request_status = 'FAILED', error_code = #{errorCode}, completed_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id} AND uid = #{uid} AND request_status = 'RUNNING'
              AND update_time <= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL #{timeoutSeconds} SECOND)
            """)
    int markFailedIfRunningAndStale(@Param("id") Long id, @Param("uid") Long uid,
                                    @Param("timeoutSeconds") long timeoutSeconds,
                                    @Param("errorCode") String errorCode);

    @Update("""
            UPDATE t_content_assist_enhanced_request
            SET result_json = NULL
            WHERE request_status IN ('SUCCEEDED', 'FALLBACK')
              AND completed_at IS NOT NULL
              AND completed_at <= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL #{retentionHours} HOUR)
              AND result_json IS NOT NULL
            ORDER BY completed_at ASC, id ASC
            LIMIT #{limit}
            """)
    int clearExpiredResults(@Param("retentionHours") int retentionHours, @Param("limit") int limit);
}
