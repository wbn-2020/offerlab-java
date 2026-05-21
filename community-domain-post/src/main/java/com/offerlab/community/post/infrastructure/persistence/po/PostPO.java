package com.offerlab.community.post.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_post_main")
public class PostPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long authorId;
    private Integer postType;
    private String title;
    private String content;
    private String coverUrl;
    private Integer visibility;
    private Integer postStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
    @Version
    private Integer version;
}
