package com.offerlab.community.post.infrastructure.persistence.projection;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PostTrustSignalsRow {
    private Long postId;
    private Integer profileAvailable;
    private Integer completenessScore;
    private LocalDateTime lastConfirmedAt;
    private String freshnessStatus;
    private Long acceptedCommentId;
    private Integer acceptedSuggestionCount;
    private Integer publicCorrectionCount;
    private Integer sourceComplete;
}
