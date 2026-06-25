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
public class DomainConfigDTO {
    private Integer domain;
    private String domainName;
    private String domainSlug;
    private String description;
    private Integer sortOrder;
    private Boolean enabled;
    private String riskLevel;
    private String postingNotice;
    private String browseNotice;
    private String interactionNotice;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
