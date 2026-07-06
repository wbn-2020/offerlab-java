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
public class OperationCandidateDTO {
    private String candidateId;
    private String candidateSource;
    private Long topicId;
    private String topicSlug;
    private String sectionKey;
    private String sourceType;
    private Long sourceId;
    private String source;
    private String title;
    private String summary;
    private String reason;
    private String reasonText;
    private String reasonDraftText;
    private Boolean reasonConfirmed;
    private String eligibility;
    private List<String> blockReasons;
    private String href;
    private Boolean degraded;
    private String fallbackReason;
    private Boolean operable;
    private PostBriefDTO post;
    private LocalDateTime createdAt;
}
