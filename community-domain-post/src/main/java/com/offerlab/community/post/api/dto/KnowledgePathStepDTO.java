package com.offerlab.community.post.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgePathStepDTO {
    private String assetId;
    private String title;
    private String targetHref;
    private Integer orderIndex;
    private String sourceNote;
}
