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
public class UserPrepOverviewDTO {
    private Long favoriteCount;
    private Long todoCount;
    private Long learningCount;
    private Long masteredCount;
    private Long reviewCount;
    private List<PrepTargetDTO> targets;
    private List<QuestionDTO> favoriteQuestions;
    private List<QuestionDTO> reviewQuestions;
    private List<QuestionDTO> recommendedQuestions;
}
