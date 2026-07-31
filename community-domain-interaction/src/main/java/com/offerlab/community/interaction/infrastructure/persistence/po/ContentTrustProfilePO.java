package com.offerlab.community.interaction.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_int_content_trust_profile")
public class ContentTrustProfilePO {
    @TableId(value = "post_id", type = IdType.INPUT)
    private Long postId;
    private Long authorUid;
    private String authorRole;
    private LocalDateTime experienceStartAt;
    private LocalDateTime experienceEndAt;
    private String applicableAudience;
    private String applicableContext;
    private String processSummary;
    private String outcomeSummary;
    private String knownLimitations;
    private String sourceSummary;
    private String interestDisclosure;
    private Integer completenessScore;
    private LocalDateTime lastConfirmedAt;
    private Long profileVersion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
