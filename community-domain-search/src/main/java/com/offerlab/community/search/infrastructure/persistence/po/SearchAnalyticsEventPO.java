package com.offerlab.community.search.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_search_analytics_event")
public class SearchAnalyticsEventPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String eventType;
    private Long uid;
    private String keyword;
    private String company;
    private String position;
    private Integer postType;
    private String sortType;
    private Integer resultCount;
    private LocalDateTime createTime;
}
