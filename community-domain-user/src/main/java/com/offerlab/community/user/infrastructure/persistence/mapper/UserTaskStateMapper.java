package com.offerlab.community.user.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.user.infrastructure.persistence.po.UserTaskStatePO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface UserTaskStateMapper extends BaseMapper<UserTaskStatePO> {

    @Select("""
            SELECT id, uid, task_type, task_code, task_date, completed, complete_source, complete_ref_id,
                   first_completed_time, create_time, update_time
            FROM t_user_task_state
            WHERE uid = #{uid}
              AND task_type = #{taskType}
              AND task_date = #{taskDate}
            ORDER BY task_code ASC
            """)
    List<UserTaskStatePO> selectByUserAndTaskDate(@Param("uid") Long uid,
                                                  @Param("taskType") String taskType,
                                                  @Param("taskDate") LocalDate taskDate);

    @Insert("""
            INSERT INTO t_user_task_state (
                id, uid, task_type, task_code, task_date, completed, complete_source, complete_ref_id, first_completed_time
            ) VALUES (
                #{state.id}, #{state.uid}, #{state.taskType}, #{state.taskCode}, #{state.taskDate},
                #{state.completed}, #{state.completeSource}, #{state.completeRefId}, #{state.firstCompletedTime}
            )
            ON DUPLICATE KEY UPDATE
                completed = GREATEST(completed, VALUES(completed)),
                complete_source = COALESCE(complete_source, VALUES(complete_source)),
                complete_ref_id = COALESCE(complete_ref_id, VALUES(complete_ref_id)),
                first_completed_time = COALESCE(first_completed_time, VALUES(first_completed_time)),
                update_time = CURRENT_TIMESTAMP(3)
            """)
    int upsertCompleted(@Param("state") UserTaskStatePO state);
}
