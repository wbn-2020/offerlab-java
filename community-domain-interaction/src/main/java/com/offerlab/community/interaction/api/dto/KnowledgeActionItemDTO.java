package com.offerlab.community.interaction.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeActionItemDTO {
    private String id;
    private String type;
    private String title;
    private String reason;
    private String status;
    private String priority;
    private String canonicalRoute;
    private Long postId;
    private LocalDateTime updatedAt;
}
