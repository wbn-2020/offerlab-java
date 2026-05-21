package com.offerlab.community.post.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_post_extension")
public class PostExtensionPO {
    @TableId(type = IdType.INPUT)
    private Long postId;
    private Integer postType;
    private String extJson;
    private LocalDateTime updateTime;
}
