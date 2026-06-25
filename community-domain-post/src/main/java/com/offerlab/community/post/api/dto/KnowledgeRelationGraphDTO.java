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
public class KnowledgeRelationGraphDTO {
    private Integer limit;
    private List<KnowledgeRelationNodeDTO> nodes;
    private List<KnowledgeRelationEdgeDTO> edges;
}
