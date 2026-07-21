package com.offerlab.community.interaction.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_int_post_outcome")
public class PostOutcomePO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long postId;
    private Long uid;
    private String outcomeType;
    private String contextNote;
    private String resultNote;
    private String visibility;
    private String publicationStatus;
    private LocalDateTime consentedAt;
    private Long reviewerUid;
    private String reviewNote;
    private LocalDateTime reviewedAt;
    private LocalDateTime followUpAt;
    private String outcomeStatus;
    private Integer revision;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
