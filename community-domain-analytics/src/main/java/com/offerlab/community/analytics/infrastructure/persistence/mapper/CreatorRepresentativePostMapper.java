package com.offerlab.community.analytics.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CreatorRepresentativePostMapper {

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_creator_representative_post'
            """)
    int tableExists();

    @Select("""
            SELECT post_id
            FROM t_creator_representative_post
            WHERE creator_uid = #{creatorUid}
              AND is_deleted = 0
            ORDER BY sort_order ASC, id ASC
            LIMIT #{limit}
            """)
    List<Long> selectActivePostIds(@Param("creatorUid") Long creatorUid, @Param("limit") int limit);

    @Update("""
            UPDATE t_creator_representative_post
            SET is_deleted = 1,
                update_time = NOW(3)
            WHERE creator_uid = #{creatorUid}
              AND is_deleted = 0
            """)
    int softDeleteByCreatorUid(@Param("creatorUid") Long creatorUid);

    @Insert("""
            INSERT INTO t_creator_representative_post (
                id, creator_uid, post_id, sort_order, create_time, update_time, is_deleted
            )
            VALUES (
                #{id}, #{creatorUid}, #{postId}, #{sortOrder}, NOW(3), NOW(3), 0
            )
            ON DUPLICATE KEY UPDATE
                sort_order = VALUES(sort_order),
                is_deleted = 0,
                update_time = NOW(3)
            """)
    int upsertActivePost(@Param("id") Long id,
                         @Param("creatorUid") Long creatorUid,
                         @Param("postId") Long postId,
                         @Param("sortOrder") int sortOrder);
}
