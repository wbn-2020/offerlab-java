package com.offerlab.community.post.api.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostUpdatedEvent {
    private Long postId;
    private Long authorId;
    private String title;
    private String content;
    private Integer visibility;
    private Integer postStatus;
    private Integer resultVersion;
    private List<Long> respondedSuggestionIds;
    private Long timestamp;
}
