package com.offerlab.community.interaction.api.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentSuggestionSubmittedEvent {
    private Long suggestionId;
    private Long postId;
    private Long postAuthorUid;
    private Long submitterUid;
    private String suggestionType;
    private Long timestamp;
}
