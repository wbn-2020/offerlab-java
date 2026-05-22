package com.offerlab.community.interaction.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_comment_report")
public class CommentReportPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long commentId;
    private Long postId;
    private Long reporterUid;
    private String reason;
    private String detail;
    private Integer reportStatus;
    private Long reviewerUid;
    private String reviewNote;
    private LocalDateTime reviewTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
