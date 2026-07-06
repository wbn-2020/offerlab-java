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
public class KnowledgeAssetOverviewDTO {
    private Integer limit;
    private LocalDateTime generatedAt;
    private List<PublicKnowledgeAssetDTO> assets;
    private List<PublicKnowledgeRelationDTO> relations;
    private List<KnowledgePathDTO> paths;
    private List<KnowledgeGapDTO> gaps;
    private List<KnowledgeAssetSnapshotDTO> snapshots;
}
