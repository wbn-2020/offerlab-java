package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelQualityGovernanceReminderIntentRow {
    private Long id;
    private Long todoId;
    private Long caseId;
    private Long assigneeUid;
    private String reminderKind;
    private Integer sequenceNo;
    private String scheduleVersion;
    private LocalDateTime scheduledAt;
    private LocalDateTime deliverBefore;
    private String status;
    private String disposition;
    private LocalDateTime nextAttemptAt;
    private String notificationDedupKey;
    private Integer intentVersion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
