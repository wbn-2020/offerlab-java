package com.offerlab.community.post.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_post_counter")
public class PostCounterPO {
    @TableId(type = IdType.INPUT)
    private Long postId;
    private Long viewCount;
    private Long likeCount;
    private Long commentCount;
    private Long favoriteCount;
    private Long shareCount;
    private LocalDateTime updateTime;
    @Version
    private Integer version;
}
