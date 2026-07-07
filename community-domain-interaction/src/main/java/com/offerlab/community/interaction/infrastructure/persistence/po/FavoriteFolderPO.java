package com.offerlab.community.interaction.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_int_favorite_folder")
public class FavoriteFolderPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long userId;
    private String name;
    private String description;
    private Integer visibility;
    private Integer sortOrder;
    private Long postCount;
    private Integer isDefault;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
