package com.offerlab.community.post.api.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OperationTopicCandidateHintCmd {
    @Size(max = 64)
    private String candidateSource;
    @Size(max = 32)
    private String source;
    private String sourceType;
    private Long sourceId;
    private Long topicId;
    @Size(max = 64)
    private String topicSlug;
    @Size(max = 64)
    private String sectionKey;
    @Size(max = 120)
    private String title;
    @Size(max = 512)
    private String href;
    @Size(max = 500)
    private String reasonText;
    @Size(max = 64)
    private String visibilityCheck;
    @Size(max = 512)
    private String returnHref;
    private Boolean persistCandidate;
}
