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
public class ContentMaintenanceTaskAttemptDTO {
    private Long id;
    private Long taskId;
    private Integer attemptNo;
    private String deliveryType;
    private Long deliveryRefId;
    private Long deliveryPostId;
    private String note;
    private Long submittedByUid;
    private LocalDateTime submittedAt;
    private String decision;
    private String reasonCode;
    private Long reviewedByUid;
    private LocalDateTime reviewedAt;
    private String reviewNote;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
