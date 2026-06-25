package com.offerlab.community.post.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_expert_cert_application")
public class ExpertCertificationApplicationPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long applicantUid;
    private Integer domain;
    private Integer status;
    private String evidenceSummary;
    private String evidenceLinksJson;
    private Integer eligibilityPassed;
    private String eligibilitySummary;
    private String eligibilitySnapshotJson;
    private Integer riskAcknowledged;
    private String riskWarning;
    private Long reviewerUid;
    private String reviewNote;
    private LocalDateTime reviewTime;
    private Long revokedBy;
    private String revokeNote;
    private LocalDateTime revokedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
}
