package com.offerlab.community.post.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_post_tag_ref")
public class PostTagRefPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long postId;
    private Long tagId;
    private LocalDateTime createTime;
}
