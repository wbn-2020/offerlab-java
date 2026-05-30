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
    private LocalDateTime createTime;
}
