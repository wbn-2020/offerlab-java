package com.offerlab.community.question.api.dto;

import lombok.Data;

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
    private String remark;

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
                || difficulty != null
                || status != null;
    }
}
