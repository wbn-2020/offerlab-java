package com.offerlab.community.post.infrastructure.persistence.projection;

import lombok.Data;

@Data
public class PostContentRevisionCandidateRow {
    private Long postId;
    private Integer resultVersion;
    private String revisionToken;
}
