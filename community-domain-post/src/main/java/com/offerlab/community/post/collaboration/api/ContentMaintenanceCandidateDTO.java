package com.offerlab.community.post.collaboration.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentMaintenanceCandidateDTO {
    private Long id;
    private Integer domain;
    private String sourceType;
    private Long sourceRefId;
    private Long sourcePostId;
    private Integer sourcePostType;
    private String title;
    private String status;
    private String assignmentStatus;
    private Boolean canClaim;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
