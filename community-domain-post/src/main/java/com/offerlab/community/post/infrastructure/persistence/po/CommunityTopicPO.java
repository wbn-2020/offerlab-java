package com.offerlab.community.post.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_community_topic")
public class CommunityTopicPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String slug;
    private String topicName;
    private String description;
    private String topicType;
    private String coverUrl;
    private Integer sortOrder;
    private Integer featured;
    private Integer topicStatus;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
}
