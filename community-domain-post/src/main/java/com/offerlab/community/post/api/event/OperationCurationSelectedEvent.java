package com.offerlab.community.post.api.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationCurationSelectedEvent {
    public static final String OPERATION_CURATION_SELECTED = "OPERATION_CURATION_SELECTED";

    private Long authorUid;
    private Long contentId;
    private String contentTitle;
    private String placementType;
    private Long placementId;
    private String placementKey;
    private String sectionKey;
    private String reason;
    private String entrance;
    private String status;
    private String eventType;
    private String dedupKey;
}
