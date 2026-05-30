package com.offerlab.community.question.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.question.infrastructure.persistence.po.UserPrepTargetPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface UserPrepTargetMapper extends BaseMapper<UserPrepTargetPO> {
    @Select("""
            SELECT *
            FROM t_user_prep_target
            WHERE uid = #{uid}
            ORDER BY
              CASE
                WHEN interview_date IS NOT NULL AND interview_date >= CURDATE() THEN 0
                WHEN interview_date IS NULL THEN 1
                ELSE 2
              END,
              interview_date ASC,
              CASE priority
                WHEN 'urgent' THEN 0
                WHEN 'high' THEN 1
                WHEN 'medium' THEN 2
                WHEN 'low' THEN 3
                ELSE 4
              END,
              create_time DESC, id DESC
            """)
    List<UserPrepTargetPO> selectByUser(@Param("uid") Long uid);

    @Insert("""
            INSERT INTO t_user_prep_target (id, uid, target_type, target_value, interview_date, priority, note)
            VALUES (#{id}, #{uid}, #{targetType}, #{targetValue}, #{interviewDate}, #{priority}, #{note})
            ON DUPLICATE KEY UPDATE
                interview_date = VALUES(interview_date),
                priority = VALUES(priority),
                note = VALUES(note),
                create_time = create_time
            """)
    int insertIgnore(@Param("id") Long id,
                     @Param("uid") Long uid,
                     @Param("targetType") String targetType,
                     @Param("targetValue") String targetValue,
                     @Param("interviewDate") LocalDate interviewDate,
                     @Param("priority") String priority,
                     @Param("note") String note);

    @Delete("""
            DELETE FROM t_user_prep_target
            WHERE id = #{id}
              AND uid = #{uid}
            """)
    int deleteByUser(@Param("id") Long id, @Param("uid") Long uid);
}
