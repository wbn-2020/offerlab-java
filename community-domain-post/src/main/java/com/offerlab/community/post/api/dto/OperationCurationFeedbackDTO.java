package com.offerlab.community.post.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationCurationFeedbackDTO {
    private Long contentId;
    private String contentTitle;
    private String placementType;
    private Long placementId;
    private String placementKey;
    private String sectionKey;
    private String reason;
    private String entrance;
    private String status;
    private LocalDateTime updateTime;
}
