package com.offerlab.community.interaction.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.interaction.infrastructure.persistence.po.LikePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface LikeMapper extends BaseMapper<LikePO> {

    @Select("""
            SELECT id, user_id, target_type, target_id, target_author_id, create_time, is_deleted
            FROM t_int_like
            WHERE user_id = #{userId}
              AND target_type = #{targetType}
              AND target_id = #{targetId}
            ORDER BY is_deleted ASC, create_time DESC
            LIMIT 1
            """)
    LikePO selectAnyByUserTarget(@Param("userId") Long userId,
                                 @Param("targetType") Integer targetType,
                                 @Param("targetId") Long targetId);

    @Update("UPDATE t_int_like SET is_deleted = 0 WHERE id = #{id}")
    int restoreById(@Param("id") Long id);

    @Update("UPDATE t_int_like SET is_deleted = 1 WHERE id = #{id} AND is_deleted = 0")
    int softDeleteById(@Param("id") Long id);
}
