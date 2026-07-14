package com.offerlab.community.analytics.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EffectiveReadSessionDTO {

    private String sessionToken;
    private Long postId;
    private Integer minimumActiveSeconds;
    private Integer minimumScrollPercent;
    private Integer heartbeatIntervalSeconds;
    private Integer heartbeatTimeoutSeconds;
    private Long nextHeartbeatSeq;
    private Integer activeSeconds;
    private Integer maxScrollPercent;
    private Boolean qualified;
    private Boolean completed;
    private Integer expiresInSeconds;
    private Long expiresAt;
}
