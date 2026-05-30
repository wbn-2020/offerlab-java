package com.offerlab.community.question.api.dto;

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
public class MockInterviewSessionDTO {
    private Long id;
    private String company;
    private String position;
    private String difficulty;
    private String focusTag;
    private Integer questionCount;
    private Integer answeredCount;
    private Integer totalScore;
    private Integer durationSeconds;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<MockInterviewAnswerDTO> answers;
}
