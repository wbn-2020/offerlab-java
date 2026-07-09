package com.offerlab.community.notification.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationReadAllResultDTO {
    private int updatedCount;
    private boolean capped;
    private long remainingUnread;
}
