package com.offerlab.community.post.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.post.api.dto.PublicPostUpdateDTO;
import com.offerlab.community.post.infrastructure.persistence.po.PostVersionHistoryPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PostVersionHistoryMapper extends BaseMapper<PostVersionHistoryPO> {

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_post_version_history'
            """)
    int tableExists();

    @Select("""
            SELECT *
            FROM t_post_version_history
            WHERE post_id = #{postId}
            ORDER BY create_time DESC, id DESC
            LIMIT #{limit}
            """)
    List<PostVersionHistoryPO> selectRecentByPost(@Param("postId") Long postId, @Param("limit") int limit);

    @Select("""
            SELECT result_version AS resultVersion,
                   public_update_summary AS publicUpdateSummary,
                   impact_scope AS impactScope,
                   create_time AS createTime
            FROM t_post_version_history
            WHERE post_id = #{postId}
              AND result_version IS NOT NULL
              AND result_version > 0
              AND public_update_summary IS NOT NULL
              AND LENGTH(TRIM(public_update_summary)) > 0
              AND EXISTS (
                    SELECT 1
                    FROM t_post_main p
                    WHERE p.id = t_post_version_history.post_id
                      AND p.is_deleted = 0
                      AND p.post_status = 1
                      AND (p.visibility = 1 OR p.visibility IS NULL)
              )
            ORDER BY create_time DESC, id DESC
            LIMIT #{limit}
            """)
    List<PublicPostUpdateDTO> listPublicUpdates(@Param("postId") Long postId, @Param("limit") int limit);
}
