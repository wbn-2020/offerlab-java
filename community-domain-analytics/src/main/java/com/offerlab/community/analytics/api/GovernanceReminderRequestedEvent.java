package com.offerlab.community.analytics.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GovernanceReminderRequestedEvent {
    private Long reminderId;
    private Long todoId;
    private Long caseId;
    private String scheduleVersion;
    private String reminderKind;
    private Integer sequenceNo;
}
