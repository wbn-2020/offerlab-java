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
public class OperationTopicSectionDTO {
    private Long id;
    private String sectionKey;
    private String title;
    private String sourceType;
    private Long sourceId;
    private String status;
    private Integer sortOrder;
    private String note;
    private String reasonText;
    private String reasonDraftText;
    private Boolean reasonConfirmed;
    private PostBriefDTO post;
    private List<OperationTopicSectionDTO> items;
}
