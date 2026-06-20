package com.offerlab.community.question.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_question_index_task")
public class QuestionIndexTaskPO {
    @TableId(type = IdType.INPUT)
    private String taskId;
    private String taskType;
    private String taskStatus;
    private Long operatorUid;
    private Integer accepted;
    private Integer indexed;
    private Integer failed;
    private Integer total;
    private String indexName;
    private String message;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
