package com.offerlab.community.infra.moderation;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ModerationKeywordHit {
    private Long id;
    private String scope;
    private Long uid;
    private Long keywordId;
    private String keyword;
    private String action;
    private String contentSummary;
    private String sourceType;
    private Long sourceId;
    private String reviewStatus;
    private Long reviewerUid;
    private String reviewNote;
    private LocalDateTime reviewTime;
    private LocalDateTime createTime;
}
