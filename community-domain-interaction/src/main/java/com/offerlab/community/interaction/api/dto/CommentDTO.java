package com.offerlab.community.interaction.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentDTO {
    private Long id;
    private Long postId;
    private Long authorId;
    private Long rootId;
    private Long parentId;
    private Long replyToUid;
    private String content;
    private Integer likeCount;
    private LocalDateTime createTime;
}
