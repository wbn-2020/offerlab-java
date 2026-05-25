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
public class PostQuestionBlockDTO {
    private String taskStatus;
    private List<QuestionDTO> questions;
    private Boolean errorVisible;
    private String errorMessage;
    private Boolean canRetry;
}
