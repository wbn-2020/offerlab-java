package com.offerlab.community.post.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_tag")
public class TagPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String tagName;
    private Integer tagType;
    private Long useCount;
    private Integer isOfficial;
    private LocalDateTime createTime;
    @TableLogic
    private Integer isDeleted;
}
