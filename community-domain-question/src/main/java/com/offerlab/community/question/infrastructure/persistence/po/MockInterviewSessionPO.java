package com.offerlab.community.question.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_mock_interview_session")
public class MockInterviewSessionPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long uid;
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
}
