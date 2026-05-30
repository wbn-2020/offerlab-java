package com.offerlab.community.question.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class QuestionDuplicateGroupDTO {
    private Long questionId;
    private Long canonicalId;
    private String normalizedHash;
    private Integer sourcePostCount;
    private Integer questionCount;
    private List<QuestionDTO> questions;
    private List<QuestionDuplicateCandidateDTO> semanticCandidates;
}
