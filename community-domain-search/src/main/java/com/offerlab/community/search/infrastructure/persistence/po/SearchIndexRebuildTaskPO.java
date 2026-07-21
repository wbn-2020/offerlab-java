package com.offerlab.community.search.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_search_index_rebuild_task")
public class SearchIndexRebuildTaskPO {
    @TableId(type = IdType.INPUT)
    private String taskId;
    private String taskType;
    private String taskStatus;
    private Long operatorUid;
    private Long checkpointId;
    private Integer indexedCount;
    private Integer failedCount;
    private Integer totalCount;
    private String indexName;
    private String lastError;
    private String lockOwner;
    private LocalDateTime lockUntil;
    private LocalDateTime heartbeatTime;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
