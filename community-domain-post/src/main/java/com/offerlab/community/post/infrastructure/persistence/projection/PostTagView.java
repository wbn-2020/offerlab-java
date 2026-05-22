package com.offerlab.community.post.infrastructure.persistence.projection;

import lombok.Data;

@Data
public class PostTagView {
    private Long postId;
    private Long id;
    private String tagName;
    private Integer tagType;
    private Long useCount;
    private Integer isOfficial;
}
