package com.offerlab.community.interaction.api.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactRequestReportReviewedEvent {
    private Long requestId;
    private Long reporterUid;
    private Long reportId;
    private String userStatus;
    private String targetPath;
    private Long timestamp;
}
