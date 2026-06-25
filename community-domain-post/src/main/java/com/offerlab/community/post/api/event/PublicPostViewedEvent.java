package com.offerlab.community.post.api.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicPostViewedEvent {
    private Long postId;
    private Long viewerUid;
    private Integer domain;
}
