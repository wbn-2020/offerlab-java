package com.offerlab.community.post.infrastructure.persistence.projection;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PostContentRevisionQueryRow {
    private Long postId;
    private Long authorId;
    private Integer visibility;
    private Integer postStatus;
    private Integer isDeleted;
    private Integer domain;
    private Boolean anonymous;
    private LocalDateTime latestEffectiveContentRevisionAt;
    private String latestEffectiveContentRevisionToken;
    private Integer effectivePublishedPostVersion;
    private String qualitySignalRevisionState;
    private LocalDateTime qualitySignalEffectiveAt;
    private String qualitySignalRevisionToken;
}
