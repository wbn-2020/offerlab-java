package com.offerlab.community.interaction.api.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentSuggestionDecidedEvent {
    private Long suggestionId;
    private Long postId;
    private Long postAuthorUid;
    private Long submitterUid;
    private Long actorUid;
    private String decision;
    private Integer resultVersion;
    private Long timestamp;
}
