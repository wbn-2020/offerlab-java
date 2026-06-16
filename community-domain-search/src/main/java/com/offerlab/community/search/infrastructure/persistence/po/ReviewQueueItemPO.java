package com.offerlab.community.search.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_review_queue")
public class ReviewQueueItemPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String sourceType;
    private Long sourceId;
    private String title;
    private String summary;
    private String riskLevel;
    private String queueStatus;
    private Long assigneeUid;
    private Long creatorUid;
    private Integer priority;
    private LocalDateTime dueTime;
    private LocalDateTime handledTime;
    private String handleResult;
    private String handleNote;
    private String extJson;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
