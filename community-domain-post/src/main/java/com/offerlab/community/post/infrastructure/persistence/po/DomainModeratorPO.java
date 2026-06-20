package com.offerlab.community.post.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_domain_moderator")
public class DomainModeratorPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long uid;
    private Integer domain;
    private Integer enabled;
    private Long createdBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
