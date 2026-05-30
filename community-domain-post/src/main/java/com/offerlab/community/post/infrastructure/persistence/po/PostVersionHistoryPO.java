package com.offerlab.community.post.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_post_version_history")
public class PostVersionHistoryPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long postId;
    private Long authorId;
    private Long editorUid;
    private Integer baseVersion;
    private Integer postType;
    private String title;
    private String content;
    private String coverUrl;
    private Integer visibility;
    private Integer postStatus;
    private String extJson;
    private String tagSnapshotJson;
    private String changeSummary;
    private LocalDateTime createTime;
}
