package com.offerlab.community.question.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user_question_progress")
public class UserQuestionProgressPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long uid;
    private Long questionId;
    private String progressStatus;
    private Integer favorite;
    private String note;
    private String mistakeReason;
    private String answerDraft;
    private String starStory;
    private LocalDateTime nextReviewAt;
    private LocalDateTime lastReviewedAt;
    private Integer reviewCount;
    private Integer reviewIntervalDays;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
