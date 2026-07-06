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
public class KnowledgeAssetSnapshotDTO {
    private String snapshotId;
    private String assetId;
    private String assetType;
    private String title;
    private String summary;
    private List<String> sections;
    private List<String> relations;
    private String sourceNote;
    private LocalDateTime archivedAt;
}
