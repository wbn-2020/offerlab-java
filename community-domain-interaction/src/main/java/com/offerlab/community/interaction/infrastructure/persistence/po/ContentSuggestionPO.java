package com.offerlab.community.interaction.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_int_content_suggestion")
public class ContentSuggestionPO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long postId;
    private Long postAuthorId;
    private Long submitterUid;
    private String suggestionType;
    private String detail;
    private String normalizedContentHash;
    private String sourceUrl;
    private Integer allowPublicAttribution;
    private String decision;
    private String authorReply;
    private String publicNote;
    private Integer resultVersion;
    private String pendingDedupKey;
    private LocalDateTime decidedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
