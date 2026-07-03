package com.offerlab.community.post.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationCandidateDTO {
    private String sourceType;
    private Long sourceId;
    private String reason;
    private Boolean operable;
    private PostBriefDTO post;
}
