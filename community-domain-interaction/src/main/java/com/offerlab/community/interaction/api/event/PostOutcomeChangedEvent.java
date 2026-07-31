package com.offerlab.community.interaction.api.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostOutcomeChangedEvent {
    private Long outcomeId;
    private Long postId;
    private Long uid;
    private Long postAuthorUid;
    private String outcomeType;
    private String visibility;
    private String publicationStatus;
    private String outcomeStatus;
    private Integer outcomeRevision;
    private String changeType;
    private Long timestamp;
}
