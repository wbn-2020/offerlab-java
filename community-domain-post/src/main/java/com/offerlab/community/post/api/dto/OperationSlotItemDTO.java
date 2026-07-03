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
public class OperationSlotItemDTO {
    private Long id;
    private String sourceType;
    private Long sourceId;
    private String status;
    private Integer sortOrder;
    private String note;
    private PostBriefDTO post;
    private OperationTopicDTO topic;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
