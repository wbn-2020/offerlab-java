package com.offerlab.community.interaction.api.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactRequestCreatedEvent {
    private Long requestId;
    private Long requesterUid;
    private Long receiverUid;
    private String sourceType;
    private Long sourceId;
    private String scene;
    private Long timestamp;
}
