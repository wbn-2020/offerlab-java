package com.offerlab.community.post.api.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OperationTopicSectionCmd {
    @Size(max = 64)
    private String title;
    private String sourceType;
    private Long sourceId;
    private String status;
    private Integer sortOrder;
    @Size(max = 500)
    private String note;
}
