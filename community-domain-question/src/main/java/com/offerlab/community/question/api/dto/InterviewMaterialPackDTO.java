package com.offerlab.community.question.api.dto;

import com.offerlab.community.post.api.dto.PostBriefDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewMaterialPackDTO {
    private Long id;
    private Long uid;
    private Long postId;
    private Integer sourcePostVersion;
    private String generationStatus;
    private String starSituation;
    private String starTask;
    private String starAction;
    private String starResult;
    private List<String> resumeBullets;
    private List<String> followUpQuestions;
    private List<String> technicalHighlights;
    private List<String> missingHints;
    private String userNote;
    private Boolean savedToPrep;
    private String provider;
    private Boolean fallbackUsed;
    private PostBriefDTO sourcePost;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
