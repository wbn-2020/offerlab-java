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
            SELECT id, user_id, post_id, folder_id, sort_order, create_time, update_time, is_deleted
            FROM t_int_favorite
            WHERE user_id = #{userId}
              AND post_id = #{postId}
            ORDER BY is_deleted ASC, create_time DESC
            LIMIT 1
            """)
    FavoritePO selectAnyByUserPost(@Param("userId") Long userId, @Param("postId") Long postId);

    @Update("UPDATE t_int_favorite SET is_deleted = 0, update_time = CURRENT_TIMESTAMP(3) WHERE id = #{id} AND is_deleted = 1")
    int restoreById(@Param("id") Long id);

    @Update("""
            UPDATE t_int_favorite
            SET folder_id = #{folderId},
                sort_order = #{sortOrder},
                is_deleted = 0,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND is_deleted = 1
            """)
    int restoreToFolder(@Param("id") Long id,
                        @Param("folderId") Long folderId,
                        @Param("sortOrder") Integer sortOrder);

    @Update("""
            UPDATE t_int_favorite
            SET folder_id = #{folderId},
                sort_order = #{sortOrder},
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND user_id = #{userId}
              AND is_deleted = 0
            """)
    int moveToFolder(@Param("id") Long id,
                     @Param("userId") Long userId,
                     @Param("folderId") Long folderId,
                     @Param("sortOrder") Integer sortOrder);

    @Update("UPDATE t_int_favorite SET is_deleted = 1, update_time = CURRENT_TIMESTAMP(3) WHERE id = #{id} AND is_deleted = 0")
    int softDeleteById(@Param("id") Long id);

    @Update("""
            UPDATE t_int_favorite f
            JOIN t_int_favorite_folder ff
              ON ff.user_id = f.user_id
             AND ff.is_default = 1
             AND ff.is_deleted = 0
            SET f.folder_id = ff.id,
                f.update_time = CURRENT_TIMESTAMP(3)
            WHERE f.user_id = #{userId}
              AND (f.folder_id IS NULL OR f.folder_id = 0)
            """)
    int backfillDefaultFolderForUser(@Param("userId") Long userId);
}
