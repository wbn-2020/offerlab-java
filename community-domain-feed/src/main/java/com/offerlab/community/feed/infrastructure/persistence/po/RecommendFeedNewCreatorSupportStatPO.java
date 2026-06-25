package com.offerlab.community.feed.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_feed_recommend_support_stat")
public class RecommendFeedNewCreatorSupportStatPO {

    @TableId(type = IdType.INPUT)
    private Long id;
    private Long viewerUid;
    private Integer domain;
    private Integer deliveredItemCount;
    private Integer supportHitItemCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
