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
public class KnowledgePathDTO {
    private String pathId;
    private String title;
    private String summary;
    private String entryAssetId;
    private List<KnowledgePathStepDTO> steps;
    private List<String> sourceRefs;
    private String pathStatus;
    private String displayState;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KnowledgePathStepDTO {
        private String assetId;
        private String title;
        private String assetType;
        private Integer order;
    }
}
