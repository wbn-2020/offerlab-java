package com.offerlab.community.question.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTaskDetailDTO {
    private AiTaskDTO task;
    private Long sourcePostId;
    private String sourcePostTitle;
    private String sourcePostSummary;
    private List<AiTaskDTO> retryRecords;
}
