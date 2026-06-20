package com.offerlab.community.question.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_interview_material_pack")
public class InterviewMaterialPackPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long uid;
    private Long postId;
    private Integer sourcePostVersion;
    private String generationStatus;
    private String starSituation;
    private String starTask;
    private String starAction;
    private String starResult;
    private String resumeBulletJson;
    private String followUpQuestionJson;
    private String technicalHighlightJson;
    private String missingHintJson;
    private String userNote;
    private Integer savedToPrep;
    private String provider;
    private Integer fallbackUsed;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
