package com.offerlab.community.post.knowledge.infrastructure;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PostKnowledgeRelationRow {
    private Long id;
    private Long sourcePostId;
    private Long targetPostId;
    private String relationType;
    private String reasonText;
    private Long proposerUid;
    private String reviewStatus;
    private String visibilityStatus;
    private Long reviewerUid;
    private String reviewNote;
    private LocalDateTime reviewedAt;
    private String riskLevel;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
