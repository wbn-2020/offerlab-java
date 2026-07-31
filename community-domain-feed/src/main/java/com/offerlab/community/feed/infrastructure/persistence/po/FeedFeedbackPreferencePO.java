package com.offerlab.community.feed.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_feed_feedback_preference")
public class FeedFeedbackPreferencePO {

    @TableId(type = IdType.INPUT)
    private Long id;
    private Long uid;
    private Long postId;
    private String action;
    private String targetType;
    private Long targetId;
    private String reason;
    private LocalDateTime expiresAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
