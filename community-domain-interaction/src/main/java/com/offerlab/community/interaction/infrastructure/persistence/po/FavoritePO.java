package com.offerlab.community.interaction.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_int_favorite")
public class FavoritePO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long userId;
    private Long postId;
    private Long folderId;
    private LocalDateTime createTime;
    private Integer isDeleted;
}
