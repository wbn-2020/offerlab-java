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
public class PublicKnowledgeRelationDTO {
    private String relationId;
    private String sourceAssetId;
    private String targetAssetId;
    private String relationType;
    private String reasonText;
    private String source;
    private String reviewStatus;
    @Builder.Default
    private String visibilityStatus = "VISIBLE";
    private String riskLevel;
    private LocalDateTime createdAt;
}
