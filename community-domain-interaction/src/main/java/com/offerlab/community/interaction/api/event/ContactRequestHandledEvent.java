package com.offerlab.community.interaction.api.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactRequestHandledEvent {
    private Long requestId;
    private Long requesterUid;
    private Long receiverUid;
    private String requestStatus;
    private Long reportId;
    private Long timestamp;
}
