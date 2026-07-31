package com.offerlab.community.search.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.search.infrastructure.persistence.po.SearchIndexRebuildTaskPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SearchIndexRebuildTaskMapper extends BaseMapper<SearchIndexRebuildTaskPO> {
    @Select("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 't_search_index_rebuild_task'")
    int tableExists();

    @Insert("""
            INSERT INTO t_search_index_rebuild_task
              (task_id, task_type, task_status, operator_uid, checkpoint_id, indexed_count, failed_count, total_count)
            VALUES (#{taskId}, #{taskType}, 'PENDING', #{operatorUid}, 0, 0, 0, 0)
            """)
    int insertPending(SearchIndexRebuildTaskPO task);

    @Select("SELECT * FROM t_search_index_rebuild_task WHERE task_status IN ('PENDING','RUNNING') ORDER BY create_time DESC LIMIT 1")
    SearchIndexRebuildTaskPO findActive();

    @Select("SELECT * FROM t_search_index_rebuild_task WHERE task_status = 'PENDING' ORDER BY create_time ASC LIMIT 1")
    SearchIndexRebuildTaskPO findPending();

    @Select("SELECT * FROM t_search_index_rebuild_task ORDER BY create_time DESC LIMIT 1")
    SearchIndexRebuildTaskPO findLatest();

    @Select("SELECT COUNT(*) FROM t_search_index_rebuild_task WHERE task_status IN ('PENDING','RUNNING')")
    int countActive();

    @Select("SELECT * FROM t_search_index_rebuild_task WHERE task_id = #{taskId}")
    SearchIndexRebuildTaskPO findByTaskId(@Param("taskId") String taskId);

    @Select("SELECT * FROM t_search_index_rebuild_task ORDER BY create_time DESC LIMIT #{limit}")
    List<SearchIndexRebuildTaskPO> listRecent(@Param("limit") int limit);

    @Update("""
            UPDATE t_search_index_rebuild_task SET task_status='RUNNING', lock_owner=#{owner}, lock_until=#{lockUntil},
              heartbeat_time=NOW(3), started_at=COALESCE(started_at,NOW(3)), update_time=NOW(3)
            WHERE task_id=#{taskId} AND task_status='PENDING'
            """)
    int markRunning(@Param("taskId") String taskId, @Param("owner") String owner,
                    @Param("lockUntil") LocalDateTime lockUntil);

    @Update("""
            UPDATE t_search_index_rebuild_task SET checkpoint_id=#{checkpointId}, indexed_count=#{indexed},
              failed_count=#{failed}, total_count=#{total}, lock_until=#{lockUntil},
              heartbeat_time=NOW(3), update_time=NOW(3)
            WHERE task_id=#{taskId} AND task_status='RUNNING' AND lock_owner=#{owner}
            """)
    int heartbeat(@Param("taskId") String taskId, @Param("owner") String owner,
                  @Param("checkpointId") long checkpointId, @Param("indexed") int indexed,
                  @Param("failed") int failed, @Param("total") int total,
                  @Param("lockUntil") LocalDateTime lockUntil);

    @Update("""
            UPDATE t_search_index_rebuild_task SET task_status=#{status}, indexed_count=#{indexed}, failed_count=#{failed},
              total_count=#{total}, index_name=#{indexName}, last_error=#{lastError}, checkpoint_id=#{checkpointId},
              lock_owner=NULL, lock_until=NULL, heartbeat_time=NOW(3), finished_at=NOW(3), update_time=NOW(3)
            WHERE task_id=#{taskId} AND task_status='RUNNING' AND lock_owner=#{owner}
            """)
    int finish(@Param("taskId") String taskId, @Param("owner") String owner, @Param("status") String status,
               @Param("indexed") int indexed, @Param("failed") int failed, @Param("total") int total,
               @Param("indexName") String indexName, @Param("lastError") String lastError,
               @Param("checkpointId") long checkpointId);

    @Update("""
            UPDATE t_search_index_rebuild_task SET task_status='FAILED', last_error='worker lease expired',
              lock_owner=NULL, lock_until=NULL, heartbeat_time=NOW(3), finished_at=NOW(3), update_time=NOW(3)
            WHERE task_status='RUNNING' AND lock_until <= NOW(3)
            """)
    int failExpiredLease();
}
