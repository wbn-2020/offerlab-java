package com.offerlab.community.post.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ContentAssistRecordMapper {

    @Insert("""
            INSERT INTO t_content_assist_record(
                id, uid, scene, provider, assist_status, domain, content_length, content_hash,
                prompt_tokens, completion_tokens, estimated_cost_micros, error_code
            ) VALUES (
                #{id}, #{uid}, #{scene}, #{provider}, #{assistStatus}, #{domain}, #{contentLength}, #{contentHash},
                #{promptTokens}, #{completionTokens}, #{estimatedCostMicros}, #{errorCode}
            )
            """)
    int insert(@Param("id") Long id,
               @Param("uid") Long uid,
               @Param("scene") String scene,
               @Param("provider") String provider,
               @Param("assistStatus") String assistStatus,
               @Param("domain") Integer domain,
               @Param("contentLength") Integer contentLength,
               @Param("contentHash") String contentHash,
               @Param("promptTokens") Integer promptTokens,
               @Param("completionTokens") Integer completionTokens,
               @Param("estimatedCostMicros") Long estimatedCostMicros,
               @Param("errorCode") String errorCode);
}
