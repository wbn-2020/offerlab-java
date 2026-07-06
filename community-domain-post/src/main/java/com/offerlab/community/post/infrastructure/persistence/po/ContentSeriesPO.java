package com.offerlab.community.post.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_content_series")
public class ContentSeriesPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long creatorUid;
    private String title;
    private String description;
    private Integer domain;
    private String coverUrl;
    private Integer visibility;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
}
