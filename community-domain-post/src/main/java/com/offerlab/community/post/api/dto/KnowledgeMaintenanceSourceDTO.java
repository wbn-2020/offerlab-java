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
public class KnowledgeMaintenanceSourceDTO {
    private String sourceKey;
    private String actionType;
    private String title;
    private String reason;
    private String status;
    private String priority;
    private String canonicalRoute;
    private Long postId;
    private LocalDateTime updatedAt;
}
