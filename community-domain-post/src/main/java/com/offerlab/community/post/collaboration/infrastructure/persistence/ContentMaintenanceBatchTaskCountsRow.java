package com.offerlab.community.post.collaboration.infrastructure.persistence;

import lombok.Data;

@Data
public class ContentMaintenanceBatchTaskCountsRow {
    private Integer openTaskCount;
    private Integer activeTaskCount;
    private Integer reassignableTaskCount;
}
