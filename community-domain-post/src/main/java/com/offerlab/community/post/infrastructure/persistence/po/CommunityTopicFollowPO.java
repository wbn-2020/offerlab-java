package com.offerlab.community.post.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_community_topic_follow")
public class CommunityTopicFollowPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long topicId;
    private Long uid;
    private LocalDateTime createTime;
    @TableLogic
    private Integer isDeleted;
}
