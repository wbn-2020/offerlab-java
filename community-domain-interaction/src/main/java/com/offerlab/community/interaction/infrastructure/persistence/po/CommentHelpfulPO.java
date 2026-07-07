package com.offerlab.community.interaction.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_int_comment_helpful")
public class CommentHelpfulPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long uid;
    private Long postId;
    private Long commentId;
    private Integer helpfulStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
