package com.offerlab.community.interaction.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_int_comment_quality_signal")
public class CommentQualitySignalPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long postId;
    private Long commentId;
    private Long rootId;
    private String signalType;
    private Integer signalStatus;
    private Long operatorUid;
    private String operatorRole;
    private String reason;
    private String source;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
