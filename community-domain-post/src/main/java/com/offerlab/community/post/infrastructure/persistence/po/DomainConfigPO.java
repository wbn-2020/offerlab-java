package com.offerlab.community.post.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_domain_config")
public class DomainConfigPO {
    @TableId(type = IdType.INPUT)
    private Integer domain;
    private String domainName;
    private String domainSlug;
    private String description;
    private Integer sortOrder;
    private Integer enabled;
    private String riskLevel;
    private String postingNotice;
    private String browseNotice;
    private String interactionNotice;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
