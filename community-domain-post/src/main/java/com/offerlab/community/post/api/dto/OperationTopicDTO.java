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
    /**
     * Read-only derived scope: "DOMAIN" when a single channel is chosen,
     * "CROSS_DOMAIN" when domain is null. Front-end must read this instead of
     * inferring cross-channel intent from a null domain.
     */
    private String topicScope;
    private String status;
    private Integer sortOrder;
    private String source;
    private Boolean degraded;
    private String fallbackReason;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private String previewToken;
    private Integer currentVersion;
    /**
     * Internal draft concurrency token. It is returned only by admin reads and
     * never written into public snapshots.
     */
    private Integer draftRevision;
    private String schemaVersion;
    private Integer sourceVersion;
    private String note;
    private List<OperationTopicSectionDTO> sections;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
