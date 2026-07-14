package com.offerlab.community.interaction.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_int_post_useful_feedback")
public class PostUsefulFeedbackPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long userId;
    private Long postId;
    private Long postAuthorId;
    private String reason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
