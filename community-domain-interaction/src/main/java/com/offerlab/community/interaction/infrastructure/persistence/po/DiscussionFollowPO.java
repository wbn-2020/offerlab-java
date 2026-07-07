package com.offerlab.community.interaction.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_int_discussion_follow")
public class DiscussionFollowPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long uid;
    private Long postId;
    private Integer followStatus;
    private Long lastReadCommentId;
    private Long lastNotifiedCommentId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
