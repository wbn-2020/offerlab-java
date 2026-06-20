package com.offerlab.community.question.api.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class InterviewMaterialUpdateCmd {
    @Size(max = 2000)
    private String starSituation;

    @Size(max = 2000)
    private String starTask;

    @Size(max = 3000)
    private String starAction;

    @Size(max = 2000)
    private String starResult;

    @Size(max = 12)
    private List<@Size(max = 300) String> resumeBullets;

    @Size(max = 12)
    private List<@Size(max = 300) String> followUpQuestions;

    @Size(max = 12)
    private List<@Size(max = 300) String> technicalHighlights;

    @Size(max = 12)
    private List<@Size(max = 300) String> missingHints;

    @Size(max = 1000)
    private String userNote;
}
