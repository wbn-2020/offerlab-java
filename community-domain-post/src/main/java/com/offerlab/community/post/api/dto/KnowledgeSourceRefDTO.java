package com.offerlab.community.post.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSourceRefDTO {
    private String source;
    private String sourceId;
    private String sourceNote;
    private KnowledgePreviewSource previewSource;
}
