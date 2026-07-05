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
public class OperationTopicDTO {
    private Long id;
    private String slug;
    private String name;
    private String description;
    private String operationType;
    private String coverUrl;
    private Integer domain;
    private String status;
    private Integer sortOrder;
    private String source;
    private Boolean degraded;
    private String fallbackReason;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private String previewToken;
    private Integer currentVersion;
    private String note;
    private List<OperationTopicSectionDTO> sections;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
