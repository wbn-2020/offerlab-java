package com.offerlab.community.question.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_interview_question")
public class InterviewQuestionPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long canonicalId;
    private String questionText;
    private String normalizedHash;
    private String answerHint;
    private String company;
    private String position;
    private String interviewRound;
    private String difficulty;
    private BigDecimal confidence;
    private Long sourcePostId;
    private Long sourceAuthorUid;
    private Integer status;
    private Integer appearCount;
    private Integer qualityScore;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
