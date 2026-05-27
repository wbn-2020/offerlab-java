package com.offerlab.community.interaction.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.interaction.infrastructure.persistence.po.CommentReportPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CommentReportMapper extends BaseMapper<CommentReportPO> {

    @Select("""
            <script>
            SELECT id,
                   comment_id AS commentId,
                   post_id AS postId,
                   reporter_uid AS reporterUid,
                   reason,
                   detail,
                   report_status AS reportStatus,
                   reviewer_uid AS reviewerUid,
                   review_note AS reviewNote,
                   review_time AS reviewTime,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_comment_report
            WHERE 1 = 1
            <if test="status != null">
              AND report_status = #{status}
            </if>
            ORDER BY create_time DESC
            LIMIT #{limit}
            </script>
            """)
    List<CommentReportPO> selectRecent(@Param("status") Integer status, @Param("limit") int limit);

    @Select("""
            SELECT id,
                   comment_id AS commentId,
                   post_id AS postId,
                   reporter_uid AS reporterUid,
                   reason,
                   detail,
                   report_status AS reportStatus,
                   reviewer_uid AS reviewerUid,
                   review_note AS reviewNote,
                   create_time AS createTime,
                   review_time AS reviewTime,
                   update_time AS updateTime
            FROM t_comment_report
            WHERE comment_id = #{commentId}
              AND reporter_uid = #{reporterUid}
              AND report_status = 0
            ORDER BY create_time DESC
            LIMIT 1
            """)
    CommentReportPO findPendingByReporter(@Param("commentId") Long commentId, @Param("reporterUid") Long reporterUid);

    @Select("""
            SELECT COUNT(*)
            FROM t_comment_report
            WHERE reporter_uid = #{reporterUid}
              AND create_time >= #{since}
            """)
    long countRecentByReporter(@Param("reporterUid") Long reporterUid, @Param("since") LocalDateTime since);

    @Update("""
            UPDATE t_comment_report
            SET report_status = #{status},
                reviewer_uid = #{reviewerUid},
                review_note = #{reviewNote},
                review_time = NOW(3)
            WHERE id = #{id}
              AND report_status = 0
            """)
    int reviewPending(@Param("id") Long id,
                      @Param("status") int status,
                      @Param("reviewerUid") Long reviewerUid,
                      @Param("reviewNote") String reviewNote);
}
