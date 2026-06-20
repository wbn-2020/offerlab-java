package com.offerlab.community.question.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.question.infrastructure.persistence.po.QuestionIndexTaskPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface QuestionIndexTaskMapper extends BaseMapper<QuestionIndexTaskPO> {

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_question_index_task'
            """)
    int tableExists();

    @Insert("""
            INSERT INTO t_question_index_task (
                task_id, task_type, task_status, operator_uid, accepted, indexed, failed, total, index_name, message
            ) VALUES (
                #{taskId}, #{taskType}, #{taskStatus}, #{operatorUid}, #{accepted}, #{indexed}, #{failed}, #{total}, #{indexName}, #{message}
            )
            """)
    int insertTask(QuestionIndexTaskPO task);

    @Update("""
            UPDATE t_question_index_task
            SET task_status = 'RUNNING',
                message = NULL,
                update_time = NOW(3)
            WHERE task_id = #{taskId}
              AND task_status IN ('PENDING', 'FAILED')
            """)
    int markRunning(@Param("taskId") String taskId);

    @Update("""
            UPDATE t_question_index_task
            SET task_status = #{status},
                accepted = #{accepted},
                indexed = #{indexed},
                failed = #{failed},
                total = #{total},
                index_name = #{indexName},
                message = #{message},
                update_time = NOW(3)
            WHERE task_id = #{taskId}
            """)
    int finish(@Param("taskId") String taskId,
               @Param("status") String status,
               @Param("accepted") int accepted,
               @Param("indexed") int indexed,
               @Param("failed") int failed,
               @Param("total") int total,
               @Param("indexName") String indexName,
               @Param("message") String message);

    @Update("""
            UPDATE t_question_index_task
            SET task_status = 'PENDING',
                accepted = 0,
                indexed = 0,
                failed = 0,
                total = 0,
                message = NULL,
                update_time = NOW(3)
            WHERE task_id = #{taskId}
              AND task_status = 'FAILED'
            """)
    int markRetry(@Param("taskId") String taskId);

    @Select("""
            SELECT *
            FROM t_question_index_task
            WHERE task_id = #{taskId}
            """)
    QuestionIndexTaskPO findByTaskId(@Param("taskId") String taskId);

    @Select("""
            SELECT *
            FROM t_question_index_task
            WHERE task_type = #{taskType}
              AND task_status IN ('PENDING', 'RUNNING')
            ORDER BY create_time DESC
            LIMIT 1
            """)
    QuestionIndexTaskPO findActiveRebuildTask(@Param("taskType") String taskType);

    @Select("""
            SELECT *
            FROM t_question_index_task
            ORDER BY create_time DESC
            LIMIT #{limit}
            """)
    List<QuestionIndexTaskPO> listRecent(@Param("limit") int limit);
}
