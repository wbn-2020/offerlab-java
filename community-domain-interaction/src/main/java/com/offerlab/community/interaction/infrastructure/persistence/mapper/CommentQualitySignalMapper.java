package com.offerlab.community.interaction.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.interaction.infrastructure.persistence.po.CommentQualitySignalPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CommentQualitySignalMapper extends BaseMapper<CommentQualitySignalPO> {

    @Select("""
            SELECT id, post_id, comment_id, root_id, signal_type, signal_status,
                   operator_uid, operator_role, reason, source,
                   create_time, update_time, is_deleted
            FROM t_int_comment_quality_signal
            WHERE comment_id = #{commentId}
              AND signal_type = #{signalType}
            ORDER BY is_deleted ASC, update_time DESC
            LIMIT 1
            """)
    CommentQualitySignalPO selectByCommentAndType(@Param("commentId") Long commentId,
                                                  @Param("signalType") String signalType);

    @Select("""
            SELECT id, post_id, comment_id, root_id, signal_type, signal_status,
                   operator_uid, operator_role, reason, source,
                   create_time, update_time, is_deleted
            FROM t_int_comment_quality_signal
            WHERE post_id = #{postId}
              AND signal_type = #{signalType}
              AND signal_status = 1
              AND is_deleted = 0
            ORDER BY update_time DESC
            LIMIT #{limit}
            """)
    List<CommentQualitySignalPO> selectActiveByPostAndType(@Param("postId") Long postId,
                                                           @Param("signalType") String signalType,
                                                           @Param("limit") int limit);

    @Select("""
            <script>
            SELECT id, post_id, comment_id, root_id, signal_type, signal_status,
                   operator_uid, operator_role, reason, source,
                   create_time, update_time, is_deleted
            FROM t_int_comment_quality_signal
            WHERE signal_status = 1
              AND is_deleted = 0
              AND comment_id IN
              <foreach collection="commentIds" item="commentId" open="(" separator="," close=")">
                #{commentId}
              </foreach>
            </script>
            """)
    List<CommentQualitySignalPO> selectActiveByCommentIds(@Param("commentIds") List<Long> commentIds);

    @Update("""
            UPDATE t_int_comment_quality_signal
            SET signal_status = #{signalStatus},
                operator_uid = #{operatorUid},
                operator_role = #{operatorRole},
                reason = #{reason},
                source = #{source},
                is_deleted = CASE WHEN #{signalStatus} = 1 THEN 0 ELSE 1 END,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    int updateSignalStatus(@Param("id") Long id,
                           @Param("signalStatus") int signalStatus,
                           @Param("operatorUid") Long operatorUid,
                           @Param("operatorRole") String operatorRole,
                           @Param("reason") String reason,
                           @Param("source") String source);

    @Update("""
            UPDATE t_int_comment_quality_signal
            SET signal_status = 0,
                is_deleted = 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE post_id = #{postId}
              AND signal_type = #{signalType}
              AND signal_status = 1
              AND is_deleted = 0
              AND comment_id != #{commentId}
            """)
    int deactivateOtherActiveByPostAndType(@Param("postId") Long postId,
                                           @Param("signalType") String signalType,
                                           @Param("commentId") Long commentId);
}
