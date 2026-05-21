package com.offerlab.community.interaction.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_int_comment")
public class CommentPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long postId;
    private Long postAuthorId;
    private Long authorId;
    private Long rootId;
    private Long parentId;
    private Long replyToUid;
    private String content;
    private Integer likeCount;
    private Integer commentStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
}
