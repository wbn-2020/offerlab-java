package com.offerlab.community.question.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuestionDuplicateCandidateDTO {
    private QuestionDTO question;
    private Integer similarityScore;
    private String reason;
}
