package com.offerlab.community.interaction.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_int_like")
public class LikePO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long userId;
    private Integer targetType;
    private Long targetId;
    private Long targetAuthorId;
    private LocalDateTime createTime;
    private Integer isDeleted;
}
