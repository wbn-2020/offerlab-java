package com.offerlab.community.question.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_interview_question_tag")
public class InterviewQuestionTagPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long questionId;
    private Long tagId;
    private LocalDateTime createTime;
}
