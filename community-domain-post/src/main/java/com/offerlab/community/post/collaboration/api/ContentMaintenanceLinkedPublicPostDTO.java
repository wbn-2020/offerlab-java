package com.offerlab.community.post.collaboration.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentMaintenanceLinkedPublicPostDTO {
    private Long postId;
    private Integer domain;
    private Integer postType;
    private String title;
    private String postHref;
    private String availability;
}
