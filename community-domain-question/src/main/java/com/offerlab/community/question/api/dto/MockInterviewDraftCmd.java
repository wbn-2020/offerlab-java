package com.offerlab.community.question.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class MockInterviewDraftCmd {
    @Min(0)
    @Max(7200)
    private Integer durationSeconds;

    @Valid
    private List<AnswerCmd> answers;

    @Data
    public static class AnswerCmd {
        @NotNull
        @Positive
        private Long questionId;

        @Size(max = 4000)
        private String answerText;

        @Size(max = 1000)
        private String selfReview;

        @Min(0)
        @Max(5)
        private Integer score;
    }
}