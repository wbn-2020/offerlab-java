package com.offerlab.community.post.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_operation_topic")
public class OperationTopicPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String slug;
    private String topicName;
    private String description;
    private String operationType;
    private String coverUrl;
    private Integer domain;
    private String topicStatus;
    private Integer sortOrder;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private String previewToken;
    private Integer currentVersion;
    private String publishedSnapshotJson;
    private String rollbackSnapshotJson;
    private String note;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
}
