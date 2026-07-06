package com.offerlab.community.post.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeGapDTO {
    private String gapId;
    private String title;
    private String reasonText;
    private String source;
    private List<String> sourceRefs;
    private Boolean minSampleMet;
    private String reviewStatus;
    private String targetStage;
}
