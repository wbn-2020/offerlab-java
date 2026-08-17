package com.offerlab.community.post.reference.infrastructure.persistence;

import com.offerlab.community.post.reference.infrastructure.persistence.PostReferenceRows.PostAccessRow;
import com.offerlab.community.post.reference.infrastructure.persistence.PostReferenceRows.ReferenceRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PostReferenceMapper {

    @Select("""
            SELECT id,
                   author_id AS authorId,
                   visibility,
                   post_status AS postStatus,
                   is_deleted AS isDeleted
            FROM t_post_main
            WHERE id = #{postId}
              AND is_deleted = 0
            FOR UPDATE
            """)
    PostAccessRow selectPostForUpdate(@Param("postId") Long postId);

    @Select("""
            SELECT COUNT(*)
            FROM t_post_main
            WHERE id = #{postId}
              AND is_deleted = 0
              AND post_status = 1
              AND visibility = 1
              AND content_environment = 'COMMUNITY'
            """)
    int countPublicPost(@Param("postId") Long postId);

    @Select("""
            SELECT id,
                   post_id AS postId,
                   owner_uid AS ownerUid,
                   reference_type AS referenceType,
                   title,
                   url,
                   normalized_url AS normalizedUrl,
                   source_domain AS sourceDomain,
                   note,
                   broken_reason AS brokenReason,
                   reference_status AS referenceStatus,
                   sort_order AS sortOrder,
                   revision,
                   last_confirmed_at AS lastConfirmedAt,
                   create_time AS createTime,
                   update_time AS updateTime,
                   is_deleted AS isDeleted
            FROM t_post_reference
            WHERE post_id = #{postId}
              AND is_deleted = 0
            ORDER BY sort_order ASC, id ASC
            """)
    List<ReferenceRow> selectActiveByPostId(@Param("postId") Long postId);

    @Select("""
            SELECT id,
                   post_id AS postId,
                   owner_uid AS ownerUid,
                   reference_type AS referenceType,
                   title,
                   url,
                   normalized_url AS normalizedUrl,
                   source_domain AS sourceDomain,
                   note,
                   broken_reason AS brokenReason,
                   reference_status AS referenceStatus,
                   sort_order AS sortOrder,
                   revision,
                   last_confirmed_at AS lastConfirmedAt,
                   create_time AS createTime,
                   update_time AS updateTime,
                   is_deleted AS isDeleted
            FROM t_post_reference
            WHERE owner_uid = #{ownerUid}
              AND reference_status = 'BROKEN'
              AND is_deleted = 0
            ORDER BY update_time DESC, id DESC
            LIMIT #{limit}
            """)
    List<ReferenceRow> listBrokenOwned(@Param("ownerUid") Long ownerUid,
                                      @Param("limit") int limit);

    @Select("""
            SELECT r.id,
                   r.post_id AS postId,
                   r.owner_uid AS ownerUid,
                   r.reference_type AS referenceType,
                   r.title,
                   r.url,
                   r.normalized_url AS normalizedUrl,
                   r.source_domain AS sourceDomain,
                   r.note,
                   r.broken_reason AS brokenReason,
                   r.reference_status AS referenceStatus,
                   r.sort_order AS sortOrder,
                   r.revision,
                   r.last_confirmed_at AS lastConfirmedAt,
                   r.create_time AS createTime,
                   r.update_time AS updateTime,
                   r.is_deleted AS isDeleted
            FROM t_post_reference r
            JOIN t_post_main p
              ON p.id = r.post_id
             AND p.author_id = #{ownerUid}
             AND p.is_deleted = 0
            WHERE r.owner_uid = #{ownerUid}
              AND r.reference_status = 'BROKEN'
              AND r.is_deleted = 0
              AND (
                    #{cursorTime} IS NULL
                    OR r.update_time < #{cursorTime}
                    OR (r.update_time = #{cursorTime} AND r.id < #{cursorId})
                  )
            ORDER BY r.update_time DESC, r.id DESC
            LIMIT #{limit}
            """)
    List<ReferenceRow> listBrokenOwnedAfter(@Param("ownerUid") Long ownerUid,
                                           @Param("cursorTime") LocalDateTime cursorTime,
                                           @Param("cursorId") Long cursorId,
                                           @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM t_post_reference r
            JOIN t_post_main p
              ON p.id = r.post_id
             AND p.author_id = #{ownerUid}
             AND p.is_deleted = 0
            WHERE r.owner_uid = #{ownerUid}
              AND r.reference_status = 'BROKEN'
              AND r.is_deleted = 0
            """)
    long countBrokenOwned(@Param("ownerUid") Long ownerUid);

    @Select("""
            SELECT id,
                   post_id AS postId,
                   owner_uid AS ownerUid,
                   reference_type AS referenceType,
                   title,
                   url,
                   normalized_url AS normalizedUrl,
                   source_domain AS sourceDomain,
                   note,
                   broken_reason AS brokenReason,
                   reference_status AS referenceStatus,
                   sort_order AS sortOrder,
                   revision,
                   last_confirmed_at AS lastConfirmedAt,
                   create_time AS createTime,
                   update_time AS updateTime,
                   is_deleted AS isDeleted
            FROM t_post_reference
            WHERE id = #{referenceId}
              AND post_id = #{postId}
              AND is_deleted = 0
            LIMIT 1
            """)
    ReferenceRow selectActiveById(@Param("postId") Long postId,
                                  @Param("referenceId") Long referenceId);

    @Select("""
            SELECT id
            FROM t_post_reference
            WHERE post_id = #{postId}
              AND normalized_url = #{normalizedUrl}
              AND is_deleted = 0
            LIMIT 1
            """)
    Long selectActiveIdByNormalizedUrl(@Param("postId") Long postId,
                                       @Param("normalizedUrl") String normalizedUrl);

    @Select("""
            SELECT COALESCE(MAX(sort_order), -1)
            FROM t_post_reference
            WHERE post_id = #{postId}
              AND is_deleted = 0
            """)
    Integer selectMaxSortOrder(@Param("postId") Long postId);

    @Insert("""
            INSERT INTO t_post_reference
                (id, post_id, owner_uid, reference_type, title, url, normalized_url,
                 source_domain, note, broken_reason, reference_status, sort_order,
                 revision, last_confirmed_at, create_time, update_time, is_deleted)
            VALUES
                (#{row.id}, #{row.postId}, #{row.ownerUid}, #{row.referenceType}, #{row.title},
                 #{row.url}, #{row.normalizedUrl}, #{row.sourceDomain}, #{row.note},
                 #{row.brokenReason}, #{row.referenceStatus}, #{row.sortOrder}, #{row.revision},
                 #{row.lastConfirmedAt}, #{row.createTime}, #{row.updateTime}, 0)
            """)
    int insert(@Param("row") ReferenceRow row);

    @Update("""
            UPDATE t_post_reference
            SET reference_type = #{row.referenceType},
                title = #{row.title},
                url = #{row.url},
                normalized_url = #{row.normalizedUrl},
                source_domain = #{row.sourceDomain},
                note = #{row.note},
                broken_reason = #{row.brokenReason},
                reference_status = #{row.referenceStatus},
                last_confirmed_at = #{row.lastConfirmedAt},
                revision = revision + 1,
                update_time = #{row.updateTime}
            WHERE id = #{row.id}
              AND post_id = #{row.postId}
              AND owner_uid = #{row.ownerUid}
              AND revision = #{expectedRevision}
              AND is_deleted = 0
            """)
    int updateIfRevision(@Param("row") ReferenceRow row,
                         @Param("expectedRevision") Integer expectedRevision);

    @Update("""
            UPDATE t_post_reference
            SET is_deleted = 1,
                revision = revision + 1,
                update_time = #{now}
            WHERE id = #{referenceId}
              AND post_id = #{postId}
              AND owner_uid = #{ownerUid}
              AND revision = #{expectedRevision}
              AND is_deleted = 0
            """)
    int softDeleteIfRevision(@Param("postId") Long postId,
                             @Param("referenceId") Long referenceId,
                             @Param("ownerUid") Long ownerUid,
                             @Param("expectedRevision") Integer expectedRevision,
                             @Param("now") java.time.LocalDateTime now);

    @Update("""
            UPDATE t_post_reference
            SET sort_order = #{sortOrder},
                revision = revision + 1,
                update_time = #{now}
            WHERE id = #{referenceId}
              AND post_id = #{postId}
              AND owner_uid = #{ownerUid}
              AND revision = #{expectedRevision}
              AND is_deleted = 0
            """)
    int reorderIfRevision(@Param("postId") Long postId,
                          @Param("referenceId") Long referenceId,
                          @Param("ownerUid") Long ownerUid,
                          @Param("sortOrder") Integer sortOrder,
                          @Param("expectedRevision") Integer expectedRevision,
                          @Param("now") java.time.LocalDateTime now);
}
