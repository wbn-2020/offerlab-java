package com.offerlab.community.interaction.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_int_post_trust_state")
public class PostTrustStatePO {
    @TableId(value = "post_id", type = IdType.INPUT)
    private Long postId;
    private String questionStatus;
    private Long acceptedCommentId;
    private Long duplicatePostId;
    private String freshnessStatus;
    private Long successorPostId;
    private LocalDateTime lastConfirmedAt;
    private Integer suggestionsOpen;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
