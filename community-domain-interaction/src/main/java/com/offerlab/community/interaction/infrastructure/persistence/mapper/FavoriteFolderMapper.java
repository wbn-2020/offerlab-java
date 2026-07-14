package com.offerlab.community.interaction.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.interaction.infrastructure.persistence.po.FavoriteFolderPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface FavoriteFolderMapper extends BaseMapper<FavoriteFolderPO> {

    @Select("""
            SELECT id, user_id, name, description, visibility, sort_order, post_count,
                   is_default, create_time, update_time, is_deleted
            FROM t_int_favorite_folder
            WHERE user_id = #{userId}
              AND is_default = 1
              AND is_deleted = 0
            ORDER BY id ASC
            LIMIT 1
            """)
    FavoriteFolderPO selectDefaultByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT id, user_id, name, description, visibility, sort_order, post_count,
                   is_default, create_time, update_time, is_deleted
            FROM t_int_favorite_folder
            WHERE user_id = #{userId}
              AND is_deleted = 0
            ORDER BY sort_order ASC, id ASC
            LIMIT 100
            """)
    List<FavoriteFolderPO> selectActiveByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT id
            FROM t_int_favorite_folder
            WHERE user_id = #{userId}
              AND is_default = 1
              AND is_deleted = 0
            ORDER BY id ASC
            LIMIT 1
            FOR UPDATE
            """)
    Long lockDefaultByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT COUNT(*)
            FROM t_int_favorite_folder
            WHERE user_id = #{userId}
              AND is_deleted = 0
            """)
    long countActiveByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT id, user_id, name, description, visibility, sort_order, post_count,
                   is_default, create_time, update_time, is_deleted
            FROM t_int_favorite_folder
            WHERE user_id = #{userId}
              AND visibility = 1
              AND is_deleted = 0
            ORDER BY sort_order ASC, id ASC
            LIMIT #{limit}
            """)
    List<FavoriteFolderPO> selectPublicByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM t_int_favorite_folder
            WHERE user_id = #{userId}
              AND name = #{name}
              AND is_deleted = 0
              AND (#{excludeId} IS NULL OR id <> #{excludeId})
            """)
    long countActiveByName(@Param("userId") Long userId,
                           @Param("name") String name,
                           @Param("excludeId") Long excludeId);

    @Select("""
            SELECT id, user_id, name, description, visibility, sort_order, post_count,
                   is_default, create_time, update_time, is_deleted
            FROM t_int_favorite_folder
            WHERE id = #{id}
              AND user_id = #{userId}
              AND is_deleted = 0
            LIMIT 1
            """)
    FavoriteFolderPO selectActiveByIdForUser(@Param("id") Long id, @Param("userId") Long userId);

    @Insert("""
            INSERT INTO t_int_favorite_folder (
                id, user_id, name, description, visibility, sort_order,
                post_count, is_default, create_time, update_time, is_deleted
            )
            SELECT #{id}, #{userId}, #{name}, #{description}, #{visibility}, #{sortOrder},
                   0, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0
            WHERE NOT EXISTS (
                SELECT 1
                FROM t_int_favorite_folder
                WHERE user_id = #{userId}
                  AND is_default = 1
                  AND is_deleted = 0
            )
            """)
    int insertDefaultIfMissing(@Param("id") Long id,
                               @Param("userId") Long userId,
                               @Param("name") String name,
                               @Param("description") String description,
                               @Param("visibility") Integer visibility,
                               @Param("sortOrder") Integer sortOrder);

    @Update("""
            UPDATE t_int_favorite_folder
            SET post_count = post_count + #{delta},
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND user_id = #{userId}
              AND is_deleted = 0
              AND post_count + #{delta} >= 0
            """)
    int changePostCount(@Param("id") Long id, @Param("userId") Long userId, @Param("delta") int delta);

    @Update("""
            UPDATE t_int_favorite_folder ff
            LEFT JOIN (
                SELECT folder_id, COUNT(*) AS active_count
                FROM t_int_favorite
                WHERE folder_id = #{id}
                  AND user_id = #{userId}
                  AND is_deleted = 0
                GROUP BY folder_id
            ) c ON c.folder_id = ff.id
            SET ff.post_count = COALESCE(c.active_count, 0),
                ff.update_time = CURRENT_TIMESTAMP(3)
            WHERE ff.id = #{id}
              AND ff.user_id = #{userId}
              AND ff.is_deleted = 0
            """)
    int recountPostCount(@Param("id") Long id, @Param("userId") Long userId);
}
