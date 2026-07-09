package com.offerlab.community.interaction.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactRequestStatsDTO {
    private long inboxTotal;
    private long inboxPending;
    private long inboxAccepted;
    private long inboxRejected;
    private long inboxIgnored;
    private long inboxReported;
    private long inboxCancelled;
    private long inboxExpired;
    private long outboxTotal;
    private long outboxPending;
    private long outboxAccepted;
    private long outboxRejected;
    private long outboxIgnored;
    private long outboxReported;
    private long outboxCancelled;
    private long outboxExpired;
}
