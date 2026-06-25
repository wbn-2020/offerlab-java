package com.offerlab.community.analytics.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_growth_event")
public class GrowthEventPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String eventType;
    private Long uid;
    private Integer domain;
    private Long contentId;
    private String targetType;
    private String targetValue;
    private String sourcePage;
    private String extJson;
    private LocalDateTime createTime;
}
