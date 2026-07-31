package com.offerlab.community.interaction.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrustProfileDTO {
    private Long postId;
    private Long authorUid;
    private Boolean profileAvailable;
    private TrustProfileRole authorRole;
    private LocalDateTime experienceStartAt;
    private LocalDateTime experienceEndAt;
    private String applicableAudience;
    private String applicableContext;
    private String processSummary;
    private String outcomeSummary;
    private String knownLimitations;
    private String sourceSummary;
    private String interestDisclosure;
    private Integer completenessScore;
    private LocalDateTime lastConfirmedAt;
    private Long profileVersion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
