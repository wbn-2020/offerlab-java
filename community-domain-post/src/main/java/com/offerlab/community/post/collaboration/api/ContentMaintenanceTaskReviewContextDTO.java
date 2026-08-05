package com.offerlab.community.post.collaboration.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentMaintenanceTaskReviewContextDTO {
    private ContentMaintenanceTaskDTO task;
    private ContentMaintenanceLinkedPublicPostDTO source;
    private ContentMaintenanceLinkedPublicPostDTO delivery;
    private ContentMaintenanceRevisionEvidenceDTO evidence;
    private Boolean degraded;
    private String fallbackReason;
}
