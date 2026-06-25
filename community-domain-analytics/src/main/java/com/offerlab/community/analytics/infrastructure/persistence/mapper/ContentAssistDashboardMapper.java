package com.offerlab.community.analytics.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ContentAssistDashboardMapper {

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_content_assist_record'
            """)
    int tableExists();

    @Select("""
            SELECT COUNT(*) AS totalRequests,
                   SUM(CASE WHEN assist_status = 'AI_SUCCESS' THEN 1 ELSE 0 END) AS aiSuccessRequests,
                   SUM(CASE WHEN assist_status = 'AI_FALLBACK' THEN 1 ELSE 0 END) AS fallbackRequests,
                   SUM(CASE WHEN assist_status = 'RULE_ONLY' THEN 1 ELSE 0 END) AS ruleOnlyRequests,
                   COUNT(DISTINCT uid) AS uniqueUsers,
                   COALESCE(SUM(prompt_tokens), 0) AS totalPromptTokens,
                   COALESCE(SUM(completion_tokens), 0) AS totalCompletionTokens,
                   COALESCE(SUM(estimated_cost_micros), 0) AS estimatedCostMicros
            FROM t_content_assist_record
            WHERE create_time >= #{since}
            """)
    Map<String, Object> summary(@Param("since") LocalDateTime since);

    @Select("""
            SELECT scene AS name,
                   COUNT(*) AS count,
                   COALESCE(SUM(estimated_cost_micros), 0) AS estimatedCostMicros
            FROM t_content_assist_record
            WHERE create_time >= #{since}
            GROUP BY scene
            ORDER BY COUNT(*) DESC, scene ASC
            """)
    List<Map<String, Object>> sceneStats(@Param("since") LocalDateTime since);

    @Select("""
            SELECT assist_status AS name,
                   COUNT(*) AS count,
                   COALESCE(SUM(estimated_cost_micros), 0) AS estimatedCostMicros
            FROM t_content_assist_record
            WHERE create_time >= #{since}
            GROUP BY assist_status
            ORDER BY COUNT(*) DESC, assist_status ASC
            """)
    List<Map<String, Object>> statusStats(@Param("since") LocalDateTime since);

    @Select("""
            SELECT provider AS name,
                   COUNT(*) AS count,
                   COALESCE(SUM(estimated_cost_micros), 0) AS estimatedCostMicros
            FROM t_content_assist_record
            WHERE create_time >= #{since}
            GROUP BY provider
            ORDER BY COUNT(*) DESC, provider ASC
            """)
    List<Map<String, Object>> providerStats(@Param("since") LocalDateTime since);

    @Select("""
            SELECT error_code AS name,
                   COUNT(*) AS count,
                   COALESCE(SUM(estimated_cost_micros), 0) AS estimatedCostMicros
            FROM t_content_assist_record
            WHERE create_time >= #{since}
              AND error_code IS NOT NULL
              AND error_code != ''
            GROUP BY error_code
            ORDER BY COUNT(*) DESC, error_code ASC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> errorStats(@Param("since") LocalDateTime since, @Param("limit") int limit);

    @Select("""
            SELECT scene,
                   provider,
                   error_code AS errorCode,
                   create_time AS createTime
            FROM t_content_assist_record
            WHERE create_time >= #{since}
              AND error_code IS NOT NULL
              AND error_code != ''
            ORDER BY create_time DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> recentErrors(@Param("since") LocalDateTime since, @Param("limit") int limit);
}
