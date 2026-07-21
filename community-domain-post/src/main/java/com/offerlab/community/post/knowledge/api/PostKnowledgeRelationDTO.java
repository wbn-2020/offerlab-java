package com.offerlab.community.post.knowledge.api;

import com.offerlab.community.post.api.dto.KnowledgeRiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostKnowledgeRelationDTO {
    private Long id;
    private Long sourcePostId;
    private Long targetPostId;
    private PostKnowledgeRelationType relationType;
    private String reasonText;
    private PostKnowledgeRelationReviewStatus reviewStatus;
    private PostKnowledgeRelationVisibility visibilityStatus;
    private KnowledgeRiskLevel riskLevel;
    private LocalDateTime createdAt;
}
