package com.offerlab.community.interaction.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.interaction.infrastructure.persistence.po.FavoritePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FavoriteMapper extends BaseMapper<FavoritePO> {

    @Select("""
            SELECT id, user_id, post_id, folder_id, create_time, is_deleted
            FROM t_int_favorite
            WHERE user_id = #{userId}
              AND post_id = #{postId}
            ORDER BY is_deleted ASC, create_time DESC
            LIMIT 1
            """)
    FavoritePO selectAnyByUserPost(@Param("userId") Long userId, @Param("postId") Long postId);

    @Update("UPDATE t_int_favorite SET is_deleted = 0 WHERE id = #{id} AND is_deleted = 1")
    int restoreById(@Param("id") Long id);

    @Update("UPDATE t_int_favorite SET is_deleted = 1 WHERE id = #{id} AND is_deleted = 0")
    int softDeleteById(@Param("id") Long id);
}
