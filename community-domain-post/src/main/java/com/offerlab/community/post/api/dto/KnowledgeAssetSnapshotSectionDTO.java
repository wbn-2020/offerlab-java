package com.offerlab.community.post.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeAssetSnapshotSectionDTO {
    private String sectionId;
    private String title;
    private String summary;
    private String sourceNote;
}
