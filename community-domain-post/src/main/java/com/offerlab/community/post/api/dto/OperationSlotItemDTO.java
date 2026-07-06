package com.offerlab.community.post.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

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
    private Long contentId;
    private String contentType;
    private String reasonText;
    private Integer rank;
    private String source;
    private Boolean blocked;
    private List<String> blockReasons;
    private PostBriefDTO post;
    private OperationTopicDTO topic;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
