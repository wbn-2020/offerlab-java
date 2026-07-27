package com.offerlab.community.post.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_operation_topic_section")
public class OperationTopicSectionPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long topicId;
    private String sectionKey;
    private String sectionTitle;
    private String sourceType;
    private Long sourceId;
    private String sectionStatus;
    private Integer sortOrder;
    private String note;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
}
