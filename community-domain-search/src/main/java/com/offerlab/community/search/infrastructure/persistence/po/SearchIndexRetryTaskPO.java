package com.offerlab.community.search.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_search_index_retry_task")
public class SearchIndexRetryTaskPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String dedupKey;
    private Long postId;
    private String operation;
    private Integer taskStatus;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private String lockOwner;
    private LocalDateTime lockUntil;
    private String lastError;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
