package com.offerlab.community.post.collaboration.api;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class NeedDiscoveryItemDTO {
    private Long id;
    private Long creatorUid;
    private Integer domain;
    private String sourceType;
    private Long sourceRefId;
    private String contentFormat;
    private String title;
    private String description;
    private String acceptanceCriteria;
    private String status;
    private Long claimedByUid;
    private LocalDateTime claimedAt;
    private LocalDateTime lastProgressAt;
    private Boolean stalled;
    private Long mergedIntoNeedId;
    private String resolutionType;
    private Long resolutionId;
    private Long resolutionPostId;
    private Integer followerCount;
    private Boolean followed;
    private List<String> matchReasons;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
