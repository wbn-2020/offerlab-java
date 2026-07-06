package com.offerlab.community.post.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeRelationDTO {
    private String relationId;
    private String sourceAssetId;
    private String targetAssetId;
    private KnowledgeRelationType relationType;
    private String reasonText;
    private KnowledgeRelationSource source;
    private KnowledgeRelationReviewStatus reviewStatus;
    private KnowledgeRiskLevel riskLevel;
    private String createdAt;
}
