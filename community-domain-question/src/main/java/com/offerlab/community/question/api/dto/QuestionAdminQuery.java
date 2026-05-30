package com.offerlab.community.question.api.dto;

import lombok.Data;

@Data
public class QuestionAdminQuery {
    private Integer status;
    private String keyword;
    private String company;
    private String position;
    private Integer minQualityScore;
    private Integer maxQualityScore;
    private Long sourcePostId;
    private Integer taskStatus;
    private int page = 1;
    private int pageSize = 30;
}
