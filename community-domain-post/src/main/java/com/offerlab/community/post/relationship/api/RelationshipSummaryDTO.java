package com.offerlab.community.post.relationship.api;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class RelationshipSummaryDTO {

    private long total;
    private long active;
    private long immediate;
    private long digest;
    private Map<String, Long> counts;
    private Map<String, Long> bySourceType;
    private long mutedCount;
    private LocalDateTime generatedAt;
}
