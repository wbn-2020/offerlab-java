package com.offerlab.community.post.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_post_report")
public class PostReportPO {
    @TableId(type = IdType.INPUT)
    private Long id;
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
