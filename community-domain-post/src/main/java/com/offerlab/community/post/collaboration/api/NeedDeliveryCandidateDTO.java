package com.offerlab.community.post.collaboration.api;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NeedDeliveryCandidateDTO {
    private Long id;
    private String resolutionType;
    private String title;
    private Integer domain;
    private Integer postType;
    private String publicPath;
    private Boolean eligible;
    private String eligibilityReason;
    private String ineligibleReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
