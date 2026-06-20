package com.offerlab.community.post.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_community_topic_tag")
public class CommunityTopicTagPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long topicId;
    private Long tagId;
    private LocalDateTime createTime;
}
