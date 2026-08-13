package com.offerlab.community.post.collaboration.infrastructure.persistence;

import lombok.Data;

@Data
public class ContentMaintenanceBatchTaskCoordinationTaskRow {
    private Long id;
    private String status;
    private Long assigneeUid;
}
