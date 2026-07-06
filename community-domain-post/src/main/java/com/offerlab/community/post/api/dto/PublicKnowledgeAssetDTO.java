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
public class PublicKnowledgeAssetDTO {
    private String assetId;
    private String assetType;
    private String title;
    private String summary;
    private String assetStatus;
    private String visibilityState;
    private String source;
    private String previewSource;
    private String sourceNote;
    private String targetHref;
    private Integer domain;
    private LocalDateTime updatedAt;
}
