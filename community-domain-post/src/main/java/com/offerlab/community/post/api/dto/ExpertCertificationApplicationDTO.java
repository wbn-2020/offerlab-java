package com.offerlab.community.post.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpertCertificationApplicationDTO {
    private Long id;
    private Long applicantUid;
    private Integer domain;
    private String domainName;
    private Integer status;
    private String statusLabel;
    private String evidenceSummary;
    private List<String> evidenceLinks;
    private Boolean eligibilityPassed;
    private String eligibilitySummary;
    private Boolean riskAcknowledged;
    private String riskWarning;
    private Long reviewerUid;
    private String reviewNote;
    private Long revokedBy;
    private String revokeNote;
    private Boolean autoCertified;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime reviewTime;
    private LocalDateTime revokedTime;
}
