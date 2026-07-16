package com.offerlab.community.post.collaboration.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ContentMaintenanceTaskMapper {

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_collab_content_maintenance_task'
            """)
    int tableExists();

    @Select("""
            SELECT COUNT(*)
            FROM t_user_account
            WHERE id = #{uid}
              AND is_deleted = 0
            """)
    int userExists(@Param("uid") Long uid);

    @Insert("""
            INSERT INTO t_collab_content_maintenance_task (
                id, domain, source_type, source_ref_id, source_post_id,
                created_by_uid, assignee_uid, title, detail, task_status
            ) VALUES (
                #{id}, #{domain}, #{sourceType}, #{sourceRefId}, #{sourcePostId},
                #{createdByUid}, #{assigneeUid}, #{title}, #{detail}, 'OPEN'
            )
            """)
    int insert(@Param("id") Long id,
               @Param("domain") Integer domain,
               @Param("sourceType") String sourceType,
               @Param("sourceRefId") Long sourceRefId,
               @Param("sourcePostId") Long sourcePostId,
               @Param("createdByUid") Long createdByUid,
               @Param("assigneeUid") Long assigneeUid,
               @Param("title") String title,
               @Param("detail") String detail);

    @Select("""
            SELECT id,
                   domain,
                   source_type AS sourceType,
                   source_ref_id AS sourceRefId,
                   source_post_id AS sourcePostId,
                   created_by_uid AS createdByUid,
                   assignee_uid AS assigneeUid,
                   title,
                   detail,
                   task_status AS status,
                   delivery_type AS deliveryType,
                   delivery_ref_id AS deliveryRefId,
                   delivery_post_id AS deliveryPostId,
                   delivery_note AS deliveryNote,
                   review_note AS reviewNote,
                   claimed_at AS claimedAt,
                   submitted_at AS submittedAt,
                   reviewed_by_uid AS reviewedByUid,
                   reviewed_at AS reviewedAt,
                   closed_by_uid AS closedByUid,
                   closed_at AS closedAt,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_collab_content_maintenance_task
            WHERE id = #{id}
            FOR UPDATE
            """)
    ContentMaintenanceTaskRow lockById(@Param("id") Long id);

    @Select("""
            <script>
            SELECT id,
                   domain,
                   source_type AS sourceType,
                   source_ref_id AS sourceRefId,
                   source_post_id AS sourcePostId,
                   created_by_uid AS createdByUid,
                   assignee_uid AS assigneeUid,
                   title,
                   detail,
                   task_status AS status,
                   delivery_type AS deliveryType,
                   delivery_ref_id AS deliveryRefId,
                   delivery_post_id AS deliveryPostId,
                   delivery_note AS deliveryNote,
                   review_note AS reviewNote,
                   claimed_at AS claimedAt,
                   submitted_at AS submittedAt,
                   reviewed_by_uid AS reviewedByUid,
                   reviewed_at AS reviewedAt,
                   closed_by_uid AS closedByUid,
                   closed_at AS closedAt,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_collab_content_maintenance_task
            WHERE assignee_uid = #{uid}
              <if test="status != null and status != ''">
                AND task_status = #{status}
              </if>
              AND (#{cursor} = 0 OR id &lt; #{cursor})
            ORDER BY id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ContentMaintenanceTaskRow> listMine(@Param("uid") Long uid,
                                             @Param("status") String status,
                                             @Param("cursor") long cursor,
                                             @Param("limit") int limit);

    @Select("""
            <script>
            SELECT id,
                   domain,
                   source_type AS sourceType,
                   source_ref_id AS sourceRefId,
                   source_post_id AS sourcePostId,
                   created_by_uid AS createdByUid,
                   assignee_uid AS assigneeUid,
                   title,
                   detail,
                   task_status AS status,
                   delivery_type AS deliveryType,
                   delivery_ref_id AS deliveryRefId,
                   delivery_post_id AS deliveryPostId,
                   delivery_note AS deliveryNote,
                   review_note AS reviewNote,
                   claimed_at AS claimedAt,
                   submitted_at AS submittedAt,
                   reviewed_by_uid AS reviewedByUid,
                   reviewed_at AS reviewedAt,
                   closed_by_uid AS closedByUid,
                   closed_at AS closedAt,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_collab_content_maintenance_task
            WHERE 1 = 1
              <if test="domain != null">
                AND domain = #{domain}
              </if>
              <if test="status != null and status != ''">
                AND task_status = #{status}
              </if>
              AND (#{cursor} = 0 OR id &lt; #{cursor})
            ORDER BY id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ContentMaintenanceTaskRow> listQueue(@Param("domain") Integer domain,
                                              @Param("status") String status,
                                              @Param("cursor") long cursor,
                                              @Param("limit") int limit);

    @Update("""
            UPDATE t_collab_content_maintenance_task
            SET assignee_uid = #{uid},
                task_status = 'CLAIMED',
                claimed_at = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND task_status = 'OPEN'
              AND (assignee_uid IS NULL OR assignee_uid = #{uid})
            """)
    int claim(@Param("id") Long id, @Param("uid") Long uid);

    @Update("""
            UPDATE t_collab_content_maintenance_task
            SET task_status = 'SUBMITTED',
                delivery_type = #{deliveryType},
                delivery_ref_id = #{deliveryRefId},
                delivery_post_id = #{deliveryPostId},
                delivery_note = #{note},
                submitted_at = CURRENT_TIMESTAMP(3),
                review_note = NULL,
                reviewed_by_uid = NULL,
                reviewed_at = NULL,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND task_status = 'CLAIMED'
              AND assignee_uid = #{uid}
            """)
    int submit(@Param("id") Long id,
               @Param("uid") Long uid,
               @Param("deliveryType") String deliveryType,
               @Param("deliveryRefId") Long deliveryRefId,
               @Param("deliveryPostId") Long deliveryPostId,
               @Param("note") String note);

    @Update("""
            UPDATE t_collab_content_maintenance_task
            SET task_status = 'COMPLETED',
                review_note = #{note},
                reviewed_by_uid = #{reviewerUid},
                reviewed_at = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND task_status = 'SUBMITTED'
            """)
    int approve(@Param("id") Long id, @Param("reviewerUid") Long reviewerUid, @Param("note") String note);

    @Update("""
            UPDATE t_collab_content_maintenance_task
            SET task_status = 'CLAIMED',
                review_note = #{note},
                reviewed_by_uid = #{reviewerUid},
                reviewed_at = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND task_status = 'SUBMITTED'
            """)
    int reject(@Param("id") Long id, @Param("reviewerUid") Long reviewerUid, @Param("note") String note);

    @Update("""
            UPDATE t_collab_content_maintenance_task
            SET task_status = 'CLOSED',
                review_note = #{note},
                closed_by_uid = #{closedByUid},
                closed_at = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND task_status IN ('OPEN', 'CLAIMED', 'SUBMITTED')
            """)
    int close(@Param("id") Long id, @Param("closedByUid") Long closedByUid, @Param("note") String note);
}
