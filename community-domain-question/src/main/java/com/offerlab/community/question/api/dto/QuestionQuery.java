package com.offerlab.community.question.api.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class QuestionQuery {
    private String keyword;
    private String company;
    private String position;
    private String difficulty;
    private String round;
    private String mistakeReason;
    private String progressStatus;
    private Boolean hasNote;
    private Boolean hasAnswerDraft;
    private Boolean hasStarStory;
    private List<Long> tagIds;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String sort;
    private Integer page;
    private Integer pageSize;
}
