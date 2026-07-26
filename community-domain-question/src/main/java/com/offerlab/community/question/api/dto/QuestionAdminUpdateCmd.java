package com.offerlab.community.question.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QuestionAdminUpdateCmd {
    private String questionText;
    private String answerHint;
    private String examPoint;
    private String referenceAnswer;
    private String sourceSnippet;
    private String qualityReason;
    private String company;
    private String position;
    private String interviewRound;
    private String difficulty;
    private Integer status;
    @NotNull
    private LocalDateTime expectedUpdateTime;
    @Size(max = 500)
    private String remark;
    @Size(max = 32)
    private String confirmationPhrase;

    public boolean hasEditableField() {
        return questionText != null
                || answerHint != null
                || examPoint != null
                || referenceAnswer != null
                || sourceSnippet != null
                || qualityReason != null
                || company != null
                || position != null
                || interviewRound != null
                || difficulty != null;
    }
}
