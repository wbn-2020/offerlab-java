package com.offerlab.community.feed.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedFeedbackPreferenceVO {
    private Long postId;
    private String action;
    private String targetType;
    private Long targetId;
    private String reason;
    private LocalDateTime expiresAt;
    private LocalDateTime updateTime;
}
