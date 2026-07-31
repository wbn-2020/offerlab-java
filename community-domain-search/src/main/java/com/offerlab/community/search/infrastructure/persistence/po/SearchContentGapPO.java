package com.offerlab.community.search.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_search_content_gap")
public class SearchContentGapPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String gapKey;
    private String keyword;
    private String clusterId;
    private String reasonText;
    private Integer windowDays;
    private Long searchCount;
    private Long noResultCount;
    private Long weakResultCount;
    private Integer minSampleMet;
    private String riskLevel;
    private String targetStage;
    private String gapStatus;
    private String source;
    private String sourceRefsJson;
    private String createdFrom;
    private LocalDateTime lastSeenAt;
    private Integer domain;
    private Long reviewedBy;
    private String reviewNote;
    private LocalDateTime reviewedAt;
    private Long convertedNeedId;
    private String resolutionType;
    private Long resolutionId;
    private Long resolutionPostId;
    private LocalDateTime fulfilledAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
